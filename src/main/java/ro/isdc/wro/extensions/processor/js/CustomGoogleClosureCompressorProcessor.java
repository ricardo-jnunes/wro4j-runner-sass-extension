package ro.isdc.wro.extensions.processor.js;

import java.nio.charset.Charset;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;

import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.model.group.processor.Minimize;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.SupportedResourceType;

@Minimize
@SupportedResourceType(ResourceType.JS)
public class CustomGoogleClosureCompressorProcessor extends GoogleClosureCompressorProcessor {
	public static final String ALIAS = "googleClosureEcma6";

	@Override
	protected CompilerOptions newCompilerOptions() {
		final CompilerOptions options = new CompilerOptions();
		/**
		 * According to John Lenz from the Closure Compiler project, if you are using
		 * the Compiler API directly, you should specify a CodingConvention.
		 * {@link http://code.google.com/p/wro4j/issues/detail?id=155}
		 */
		options.setCodingConvention(new ClosureCodingConvention());
		// use the wro4j encoding by default
		//options.setOutputCharset(Charset.forName(super.getEncoding()));
		options.setOutputCharset(Charset.forName(WroConfiguration.DEFAULT_ENCODING));
		// set it to warning, otherwise compiler will fail
		options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.WARNING);
		return options;
	}

}