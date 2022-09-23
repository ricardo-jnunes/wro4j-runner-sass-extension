package ro.isdc.wro.runner.processor.css;

import java.io.IOException;

import de.larsgrefer.sass.embedded.SassCompilationFailedException;
import de.larsgrefer.sass.embedded.SassCompiler;
import de.larsgrefer.sass.embedded.SassCompilerFactory;

/**
 * The SCSS Content compiler.
 */
public class SassContentCompiler {

	SassCompiler sassCompiler;

	public void init() throws IOException {
		sassCompiler = SassCompilerFactory.bundled();
	}

	public void close() throws IOException {
		sassCompiler.close();
	}

	public String compileContent(String content) throws IOException, SassCompilationFailedException {

		return sassCompiler.compileScssString(content).getCss();
	}

}