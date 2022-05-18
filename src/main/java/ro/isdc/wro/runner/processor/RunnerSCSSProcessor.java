package ro.isdc.wro.runner.processor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

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
	public static final String ALIAS = "scssCss";

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
	public void process(final Resource resource, final Reader reader, final Writer writer) throws IOException {
		final String content = IOUtils.toString(reader);
		final ScssEngine engine = enginePool.getObject();
		try {
			final String filename = resource == null ? "noName.js" : resource.getUri();
			LOG.info(filename);
			LOG.info(content);
			LOG.info("Initing Engine");
			writer.write(engine.process(filename, content));
		} catch (final WroRuntimeException e) {
			onException(e);
			final String resourceUri = resource == null ? StringUtils.EMPTY : "[" + resource.getUri() + "]";
			LOG.warn("Exception while applying " + getClass().getSimpleName() + " processor on the " + resourceUri
					+ " resource, no processing applied...", e);
		} finally {
			reader.close();
			writer.close();
			enginePool.returnObject(engine);
		}
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
