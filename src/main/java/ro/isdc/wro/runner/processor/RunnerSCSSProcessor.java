package ro.isdc.wro.runner.processor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.html.CSS;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.extensions.processor.support.ObjectPoolHelper;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.SupportedResourceType;
import ro.isdc.wro.model.resource.processor.Destroyable;
import ro.isdc.wro.model.resource.processor.ResourcePostProcessor;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.runner.processor.css.ScssEngine;
import ro.isdc.wro.util.ObjectFactory;

/**
 * Custom extension of {@link CSS SCSS} created for wro4j-runner.
 *
 * @author Ricardo Nunes
 * @since 0.1.0
 */
@SupportedResourceType(ResourceType.CSS)
public class RunnerSCSSProcessor implements ResourcePreProcessor, ResourcePostProcessor, Destroyable {
	private static final Logger LOG = LoggerFactory.getLogger(RunnerSCSSProcessor.class);
	public static final String ALIAS = "scssCssCompiler";

	private static final Pattern RULE_PATTERN_SASS_MODULE = Pattern.compile("(@use (?='sass).*)");
	private static final Pattern RULE_PATTERN_FIRST_RULE = Pattern.compile("((@use|@import).*;)");


	private final ObjectPoolHelper<ScssEngine> enginePool;

	public RunnerSCSSProcessor() {
		enginePool = new ObjectPoolHelper<ScssEngine>(new ObjectFactory<ScssEngine>() {
			@Override
			public ScssEngine create() {
				return new ScssEngine();
			}
		});
	}

	@Override
	public void process(final Resource resource, final Reader reader, final Writer writer) throws IOException, WroRuntimeException {
		String content = IOUtils.toString(reader);
		final ScssEngine engine = enginePool.getObject();
		try {
			final String filename = resource == null ? "noName" : resource.getUri();
			LOG.debug("processing filename: " + filename);
			
			content = rulesFirst(content);
			
			//LOG.info("processing content: \n" + content);
			writer.write(engine.process(filename, content));
		} catch (final WroRuntimeException e) {
			final String resourceUri = resource == null ? StringUtils.EMPTY : "[" + resource.getUri() + "]";
			LOG.warn("Exception while applying " + getClass().getSimpleName() + " processor on the " + resourceUri
					+ " resource, no processing applied...", e);
			onException(e);
		} finally {
			reader.close();
			writer.close();
			enginePool.returnObject(engine);
		}
	}

	private String rulesFirst(String content) {
		LinkedHashSet<String> rulesFirst = new LinkedHashSet<String>();
		Matcher mrule = RULE_PATTERN_FIRST_RULE.matcher(content);
		while(mrule.find()) {
			rulesFirst.add(mrule.group(0));
		}
		content = mrule.replaceAll("");
		StringBuilder builder = new StringBuilder();
		for (String value : rulesFirst) {
		    builder.append(value).append(System.lineSeparator());
		}
		return builder.toString() + content;
	}

	@Override
	public void process(Reader reader, Writer writer) throws IOException {
		process(null, reader, writer);
	}

	@Override
	public void destroy() throws Exception {
		enginePool.destroy();
	}

	protected void onException(final WroRuntimeException e) {
		throw e;
	}

}
