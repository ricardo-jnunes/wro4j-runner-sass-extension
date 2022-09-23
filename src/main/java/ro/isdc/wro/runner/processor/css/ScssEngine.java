package ro.isdc.wro.runner.processor.css;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.larsgrefer.sass.embedded.SassCompilationFailedException;
import ro.isdc.wro.WroRuntimeException;

public class ScssEngine {
	private static final Logger LOG = LoggerFactory.getLogger(ScssEngine.class);

	protected SassContentCompiler compiler;

	public ScssEngine() {
		compiler = initCompiler();
	}

	public String process(String filename, String content) {

		if (isEmpty(content)) {
			return StringUtils.EMPTY;
		}
		try {
			synchronized (this) {
				
				return processContent(content);
			}
		} catch (final Exception e) {
			throw new WroRuntimeException(e.getMessage(), e);
		} finally {
			close();
		}
	}

	protected SassContentCompiler initCompiler() {
		SassContentCompiler mCompiler = new SassContentCompiler();
		try {
			mCompiler.init();
		} catch (IOException e) {
			LOG.error("Error init compiler: ", e);
		}
		return mCompiler;
	}

	protected String processContent(String content) throws SassCompilationFailedException {
		// LOG.info("compiling content: {}", content);

		String out = null;
		try {
			out = compiler.compileContent(content);
		} catch (SassCompilationFailedException e) {
			LOG.error("Error compiling content: ", e);
			throw e;
		} catch (IOException e) {
			LOG.error("IO fatal Error: ", e);
		}

		// LOG.info("compiled content: {}", content);
		LOG.debug("compilation finished");

		return out;
	}

	protected void close() {
		try {
			compiler.close();
		} catch (IOException e) {
			LOG.error("IO fatal Error while close: ", e);
		}
	}

}
