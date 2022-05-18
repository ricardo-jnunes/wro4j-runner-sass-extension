package ro.isdc.wro.runner.processor.css;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bit3.jsass.CompilationException;
import io.bit3.jsass.Output;
import io.bit3.jsass.OutputStyle;
import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.runner.processor.css.SassContentCompiler.InputSyntax;

public class ScssEngine {

	private static final Object GLOBAL_LOCK = new Object();

	private static final Pattern PATTERN_ERROR_JSON_LINE = Pattern.compile("[\"']line[\"'][:\\s]+([0-9]+)");
	private static final Pattern PATTERN_ERROR_JSON_COLUMN = Pattern.compile("[\"']column[\"'][:\\s]+([0-9]+)");

	private static final Logger LOG = LoggerFactory.getLogger(ScssEngine.class);

	protected SassContentCompiler compiler;

	public ScssEngine() {
		compiler = initCompiler();
	}

	public String process(String filename, String content) {

		LOG.info("Compiling " + content + " to " + filename);

		if (isEmpty(content)) {
			return StringUtils.EMPTY;
		}
		try {
			synchronized (this) {
				return processContent(content).getCss();
			}
		} catch (final Exception e) {
			throw new WroRuntimeException(e.getMessage(), e);
		}
	}

	protected SassContentCompiler initCompiler() {
		SassContentCompiler compiler = new SassContentCompiler();
		compiler.setGenerateSourceMap(false);
		compiler.setInputSyntax(InputSyntax.scss);
		compiler.setOutputStyle(OutputStyle.NESTED);
		compiler.setPrecision(5);
		return compiler;
	}

	protected Output processContent(String content) {
		LOG.debug("Processing content: " + content);

		Output out = null;
		try {
			out = compiler.compileContent(content);
		} catch (CompilationException e) {
			LOG.error(e.getMessage());
			LOG.debug("Error compiling content: ", e);

			// we need this info from json:
			// "line": 4,
			// "column": 1,
			// - a full blown parser for this would probably be an overkill, let's just
			// regex
			String errorJson = e.getErrorJson();
			int line = 0;
			int column = 0;
			if (errorJson != null) { // defensive, in case we don't always get it
				Matcher lineMatcher = PATTERN_ERROR_JSON_LINE.matcher(errorJson);
				if (lineMatcher.find()) {
					try {
						line = Integer.parseInt(lineMatcher.group(1));
						// in case regex doesn't cut it anymore
					} catch (IndexOutOfBoundsException e1) {
						LOG.error("Failed to parse error json line (IOOB): " + e1.getMessage());
						LOG.debug("Error:", e1);
					} catch (NumberFormatException e1) {
						LOG.error("Failed to parse error json line: " + e1.getMessage());
						LOG.debug("Error:", e1);
					}
				}
				Matcher columnMatcher = PATTERN_ERROR_JSON_COLUMN.matcher(errorJson);
				if (columnMatcher.find()) {
					try {
						column = Integer.parseInt(columnMatcher.group(1));
						// in case regex doesn't cut it anymore
					} catch (IndexOutOfBoundsException e1) {
						LOG.error("Failed to parse error json column (IOOB): " + e1.getMessage());
						LOG.debug("Error:", e1);
					} catch (NumberFormatException e1) {
						LOG.error("Failed to parse error json column: " + e1.getMessage());
						LOG.debug("Error:", e1);
					}
				}
			}
		}

		LOG.debug("Compilation finished.");

		return out;
	}

}
