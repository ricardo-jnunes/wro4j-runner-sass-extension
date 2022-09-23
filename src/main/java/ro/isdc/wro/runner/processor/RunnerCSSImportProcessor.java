package ro.isdc.wro.runner.processor;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.config.Context;
import ro.isdc.wro.model.group.Inject;
import ro.isdc.wro.model.group.processor.PreProcessorExecutor;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.SupportedResourceType;
import ro.isdc.wro.model.resource.locator.UrlUriLocator;
import ro.isdc.wro.model.resource.locator.factory.UriLocatorFactory;
import ro.isdc.wro.model.resource.processor.ImportAware;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.model.resource.processor.support.CssImportInspector;
import ro.isdc.wro.model.resource.processor.support.ProcessingCriteria;
import ro.isdc.wro.model.resource.processor.support.ProcessingType;
import ro.isdc.wro.util.StringUtils;
import ro.isdc.wro.util.WroUtil;

@SupportedResourceType(ResourceType.CSS)
public class RunnerCSSImportProcessor implements ResourcePreProcessor, ImportAware {
	private static final Logger LOG = LoggerFactory.getLogger(RunnerCSSImportProcessor.class);
	public static final String ALIAS = "scssCssImport";

	@Inject
	private PreProcessorExecutor preProcessorExecutor;

	private static final Pattern RULE_PATTERN_TOREMOVE_WITH_NAMESPACE_USE = Pattern.compile("(@use '([a-zA-Z0-9_/..]+)(.*\\s)(\\w+);)");
	private static final Pattern RULE_PATTERN_TOREMOVE_WITHOUT_NAMESPACE_USE = Pattern.compile("(@use '([a-zA-Z0-9_/..]+)(.*\\sas.*\\*);)");
	private static final Pattern RULE_PATTERN_TOREMOVE_USE = Pattern.compile("(@use '([a-zA-Z0-9\\-_/..]+)';)");
	private static final Pattern RULE_PATTERN_FIRST_RULE = Pattern.compile("((@use|@import).*;)");

	@Inject
	private UriLocatorFactory uriLocatorFactory;
	/**
	 * A map useful for detecting deep recursion. The key (correlationId) -
	 * identifies a processing unit, while the value contains a pair between the
	 * list o processed resources and a stack holding recursive calls (value
	 * contained on this stack is not important). This map is used to ensure that
	 * the processor is thread-safe and doesn't erroneously detect recursion when
	 * running in concurrent environment (when processor is invoked from within the
	 * processor for child resources).
	 */
	private final Map<String, Pair<List<String>, Stack<String>>> contextMap = new ConcurrentHashMap<String, Pair<List<String>, Stack<String>>>() {
		/**
		 * Make sure that the get call will always return a not null object. To avoid
		 * growth of this map, it is important to call remove for each invoked get.
		 */
		@Override
		public Pair<List<String>, Stack<String>> get(final Object key) {
			Pair<List<String>, Stack<String>> result = super.get(key);
			if (result == null) {
				final List<String> list = new ArrayList<String>();
				result = ImmutablePair.of(list, new Stack<String>());
				put(key.toString(), result);
			}
			return result;
		};
	};

	/**
	 * Useful to check that there is no memory leak after processing completion.
	 * 
	 * @VisibleForTesting
	 */
	protected final Map<String, Pair<List<String>, Stack<String>>> getContextMap() {
		return contextMap;
	}

	public final void process(final Resource resource, final Reader reader, final Writer writer) throws IOException {
		final String filename = resource == null ? "noName" : resource.getUri();
		LOG.debug("importing filename: " + filename);
		validate();
		try {
			final String result = parseCss(resource, IOUtils.toString(reader));
			//LOG.info("writing to compiling processing: \n" + result);
			writer.write(result);
		} finally {
			// important to avoid memory leak
			clearProcessedImports();
			reader.close();
			writer.close();
		}
	}

	/**
	 * Checks if required fields were injected.
	 */
	private void validate() {
		Validate.notNull(uriLocatorFactory);
	}

	/**
	 * @param resource   {@link Resource} to process.
	 * @param cssContent Reader for processed resource.
	 * @return css content with all imports processed.
	 */
	private String parseCss(final Resource resource, final String cssContent) throws IOException {
		//LOG.info("import phase parsing \n {}", cssContent);
		List<String> removeVars = new ArrayList<String>();

		// fix imports
		// Process @use 'newsletter' as news
		Matcher mWithNamespace = RULE_PATTERN_TOREMOVE_WITH_NAMESPACE_USE.matcher(cssContent);
		while(mWithNamespace.find()) {
			removeVars.add(mWithNamespace.group(4));
		}
		String out = mWithNamespace.replaceAll("@import '$2.scss';");
		
		if(!removeVars.isEmpty()) {
			for (String remove : removeVars) {
				out = out.replaceAll(remove+"\\.(?!sass|scss)", "");
			}
		}
		
		// Process @use '_mobile' as *
		Matcher mWithoutNamespaceUse = RULE_PATTERN_TOREMOVE_WITHOUT_NAMESPACE_USE.matcher(out);
		out = mWithoutNamespaceUse.replaceAll("@import '$2.scss';");
		
		// Process @use 'newsletter'
		Matcher mUse = RULE_PATTERN_TOREMOVE_USE.matcher(out);
		out = mUse.replaceAll("@import '$2.scss';");
				
		if (isImportProcessed(resource.getUri())) {
			LOG.debug("[WARN] Recursive import detected: {}", resource);
			onRecursiveImportDetected();
			return "";
		}
		final String importedUri = resource.getUri().replace(File.separatorChar, '/');
		addProcessedImport(importedUri);
		final List<Resource> importedResources = findImportedResources(resource.getUri(), out);
		LOG.debug("import phase, imported resources \n {}", importedResources);
		return doTransform(out, importedResources);
	}
	
	protected String doTransform(final String cssContent, final List<Resource> foundImports) throws IOException {
		final StringBuilder sb = new StringBuilder();
		//LOG.info("Imported resources found " + foundImports.toString());
		sb.append(preProcessorExecutor.processAndMerge(foundImports,
				ProcessingCriteria.create(ProcessingType.IMPORT_ONLY, false)));
		if (!foundImports.isEmpty()) {
			LOG.debug("Imported resources found : {}", foundImports.size());
		}
		sb.append(cssContent);
		LOG.debug("imports collector: {}", foundImports);
		return removeImportStatements(sb.toString());
	}
	
	public static void main(String[] args) {
		List<String> removeVars = new ArrayList<String>();
		LinkedList<String> rulesFirst = new LinkedList<String>();
		Matcher m = RULE_PATTERN_TOREMOVE_WITH_NAMESPACE_USE.matcher(
				  "@use 'variables' as var;\r\n"
				+ "@use 'functions' as fnc;\n"
				+ "@use 'footer/_newsletter' as news;\r\n"
				+ "@use 'footer/_main' as main;"

				+ ".header__top {\r\n"
				+ "        display: flex;\r\n"
				+ "        width: 100%;\r\n"
				+ "        height: 40px;\r\n"
				+ "        @include fnc.border-color(rgba(var.$color-black, 5%), bottom);\r\n"
				+ "        order: 2;\r\n"
				+ "        position: relative;\r\n"
				+ "        top: 70px;\r\n"
				+ "    }"
				+ "@use 'sass:math';\r\n"
				+ "@use '_mobile' as *;\r\n"
				+ "@use 'footer/_justimport';\r\n");
		
		while(m.find()) {
			removeVars.add(m.group(4));
		}
		String out = m.replaceAll("@import '$2.scss';");
		System.out.println(out);
		System.out.println("--- before remove module ----");
		
		
		if(!removeVars.isEmpty()) {
			for (String remove : removeVars) {
				//out = out.replace((remove+"."),"");
				System.out.println("removing" + remove);
				out = out.replaceAll(remove+"\\.(?!sass|scss)", "");
			}
		}
		
		Matcher mWithoutUse = RULE_PATTERN_TOREMOVE_WITHOUT_NAMESPACE_USE.matcher(out);
		while(mWithoutUse.find()) {
			System.out.println(mWithoutUse.group(0));
			System.out.println(mWithoutUse.group(1));
			System.out.println(mWithoutUse.group(2));
		}
		out = mWithoutUse.replaceAll("@import '$2.scss';");
		System.out.println(out);
		
		System.out.println("removingnamesspace\n");
		
		Matcher mUse = RULE_PATTERN_TOREMOVE_USE.matcher(out);
		out = mUse.replaceAll("@import '$2.scss';");
		
		//fix rules and import need to be first
		Matcher mFirstRule = RULE_PATTERN_FIRST_RULE.matcher(out);
		while(mFirstRule.find()) {
			rulesFirst.add(mFirstRule.group(0));
		}
		
		out = mFirstRule.replaceAll("");
		
		StringBuilder builder = new StringBuilder();
		for (String value : rulesFirst) {
		    builder.append(value).append(System.lineSeparator());
		}
		String allUseImports = builder.toString();
		out = allUseImports + out;
		System.out.println(out);
	}

	private boolean isImportProcessed(final String uri) {
		return getProcessedImports().contains(uri);
	}

	private void addProcessedImport(final String importedUri) {
		final String correlationId = Context.getCorrelationId();
		contextMap.get(correlationId).getValue().push(importedUri);
		getProcessedImports().add(importedUri);
	}

	private List<String> getProcessedImports() {
		return contextMap.get(Context.getCorrelationId()).getKey();
	}

	private void clearProcessedImports() {
		final String correlationId = Context.getCorrelationId();
		final Stack<String> stack = contextMap.get(correlationId).getValue();
		if (!stack.isEmpty()) {
			stack.pop();
		}
		if (stack.isEmpty()) {
			contextMap.remove(correlationId);
		}
	}

	/**
	 * Find a set of imported resources inside a given resource.
	 */
	private List<Resource> findImportedResources(final String resourceUri, final String cssContent) throws IOException {
		// it should be sorted
		final List<Resource> imports = new ArrayList<Resource>();
		final String css = cssContent;
		final List<String> foundImports = findImports(css);
		for (final String importUrl : foundImports) {
			final Resource importedResource = createImportedResource(resourceUri, importUrl);
			// check if already exist
			if (imports.contains(importedResource)) {
				LOG.debug("[WARN] Duplicate imported resource: {}", importedResource);
			} else {
				imports.add(importedResource);
				onImportDetected(importedResource.getUri());
			}
		}
		return imports;
	}

	protected List<String> findImports(final String css) {
		return new CssImportInspector(css).findImports();
	}

	private Resource createImportedResource(final String resourceUri, final String importUrl) {
		final String absoluteUrl = UrlUriLocator.isValid(importUrl) ? importUrl
				: computeAbsoluteUrl(resourceUri, importUrl);
		return Resource.create(absoluteUrl, ResourceType.CSS);
	}

	private String computeAbsoluteUrl(final String relativeResourceUri, final String importUrl) {
		final String folder = WroUtil.getFullPath(relativeResourceUri);
		// remove '../' & normalize the path.
		final String absoluteImportUrl = StringUtils.cleanPath(folder + importUrl);
		return absoluteImportUrl;
	}

	protected String removeImportStatements(final String cssContent) {
		return new CssImportInspector(cssContent).removeImportStatements();
	}

	public boolean isImportAware() {
		// We want this processor to be applied when processing resources referred with
		// @import directive
		return true;
	}

	protected void onImportDetected(final String foundImportUri) {
	}

	protected void onRecursiveImportDetected() {
	}

}
