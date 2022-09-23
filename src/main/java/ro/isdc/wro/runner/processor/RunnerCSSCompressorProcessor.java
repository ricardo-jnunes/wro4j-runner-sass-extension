package ro.isdc.wro.runner.processor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.platform.yui.compressor.CssCompressor;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.model.group.processor.Minimize;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.SupportedResourceType;
import ro.isdc.wro.model.resource.processor.ResourcePostProcessor;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;

/**
 * Custom extension updating compression of {@link YUI CSS Compressor} created
 * for wro4j-runner.
 *
 * @author Ricardo Nunes
 * @since 0.1.0
 */
@Minimize
@SupportedResourceType(ResourceType.CSS)
public class RunnerCSSCompressorProcessor implements ResourcePreProcessor, ResourcePostProcessor {
	private static final Logger LOG = LoggerFactory.getLogger(RunnerCSSCompressorProcessor.class);
	public static final String ALIAS = "yuiCSSCompressor";

	@Override
	public void process(final Resource resource, final Reader reader, final Writer writer) throws IOException {
		try {
			//final String filename = resource == null ? "noName" : resource.getUri();
			//LOG.info("compressing filename: " + filename);
			CssCompressor compressor = new CssCompressor(reader);
			compressor.compress(writer, -1);
		} catch (final WroRuntimeException e) {
			onException(e);
			final String resourceUri = resource == null ? StringUtils.EMPTY : "[" + resource.getUri() + "]";
			LOG.warn("Exception while applying " + getClass().getSimpleName() + " processor on the " + resourceUri
					+ " resource, no processing applied...", e);
		} finally {
			reader.close();
			writer.close();
		}
	}

	@Override
	public void process(Reader reader, Writer writer) throws IOException {
		process(null, reader, writer);
	}

	protected void onException(final WroRuntimeException e) {
		throw e;
	}
	
}
