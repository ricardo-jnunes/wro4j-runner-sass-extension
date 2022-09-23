package ro.isdc.wro.runner.spi;

import java.util.HashMap;
import java.util.Map;

import ro.isdc.wro.extensions.processor.js.CustomGoogleClosureCompressorProcessor;
import ro.isdc.wro.model.resource.processor.ResourcePostProcessor;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.runner.processor.RunnerCSSCompressorProcessor;
import ro.isdc.wro.runner.processor.RunnerCSSImportProcessor;
import ro.isdc.wro.runner.processor.RunnerCssLintProcessor;
import ro.isdc.wro.runner.processor.RunnerJsHintProcessor;
import ro.isdc.wro.runner.processor.RunnerJsLintProcessor;
import ro.isdc.wro.runner.processor.RunnerSCSSProcessor;
import ro.isdc.wro.util.provider.ConfigurableProviderSupport;

public class DefaultConfigurableProvider extends ConfigurableProviderSupport {
	@Override
	public Map<String, ResourcePreProcessor> providePreProcessors() {
		final Map<String, ResourcePreProcessor> map = new HashMap<String, ResourcePreProcessor>();
		map.put(RunnerCssLintProcessor.ALIAS, new RunnerCssLintProcessor());
		map.put(RunnerJsLintProcessor.ALIAS, new RunnerJsLintProcessor());
		map.put(RunnerJsHintProcessor.ALIAS, new RunnerJsHintProcessor());
		map.put(CustomGoogleClosureCompressorProcessor.ALIAS, new CustomGoogleClosureCompressorProcessor());
		map.put(RunnerCSSImportProcessor.ALIAS, new RunnerCSSImportProcessor());
		return map;
	}

	@Override
	public Map<String, ResourcePostProcessor> providePostProcessors() {
		final Map<String, ResourcePostProcessor> map = super.providePostProcessors();
		map.put(RunnerSCSSProcessor.ALIAS, new RunnerSCSSProcessor());
		map.put(RunnerCSSCompressorProcessor.ALIAS, new RunnerCSSCompressorProcessor());
		return map;
	}
}
