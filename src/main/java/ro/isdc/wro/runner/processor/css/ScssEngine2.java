package ro.isdc.wro.runner.processor.css;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bit3.jsass.CompilationException;
import io.bit3.jsass.Output;
import io.bit3.jsass.OutputStyle;
import wrm.AbstractSassMojo;
import wrm.libsass.SassCompiler;
import wrm.libsass.SassCompiler.InputSyntax;

public class ScssEngine2 extends AbstractSassMojo {

	private static final Pattern PATTERN_ERROR_JSON_LINE = Pattern.compile("[\"']line[\"'][:\\s]+([0-9]+)");
	private static final Pattern PATTERN_ERROR_JSON_COLUMN = Pattern.compile("[\"']column[\"'][:\\s]+([0-9]+)");

	private static final Logger LOG = LoggerFactory.getLogger(ScssEngine2.class);

	public ScssEngine2() {
		project = new MavenProject();
		project.setFile(new File(
				"C:\\input"));
		inputPath = "static\\scss";
		outputPath = new File(
				"C:\\output\\static\\dist");

	}

	public String process(final String filename, final String content) throws IOException {
		try {
			execute();
		} catch (MojoExecutionException e) {
			e.printStackTrace();
		}
		return content;
	}

	@Override
	public void execute() throws MojoExecutionException {
		validateConfig();

		if ((inputPath != null) && (outputPath != null)) {

			compiler = initCompiler();

			inputPath = inputPath.replaceAll("\\\\", "/");

			LOG.info("Input Path=" + inputPath);
			LOG.info("Output Path=" + outputPath);

			try {
				compile();
			} catch (Exception e) {
				throw new MojoExecutionException("SCSS engine failed to compile", e);
			}
		}
	}

	protected SassCompiler initCompiler() {

		SassCompiler compiler = new SassCompiler();
		compiler.setGenerateSourceMap(false);
		compiler.setIncludePaths(includePath);
		compiler.setInputSyntax(InputSyntax.scss);
		compiler.setOutputStyle(OutputStyle.NESTED);
		compiler.setPrecision(5);
		return compiler;
	}

	protected void compile() throws Exception {
		LOG.info("Compiling files");
		final AtomicInteger errorCount = new AtomicInteger(0);
		final AtomicInteger fileCount = new AtomicInteger(0);
		if (inputPath != null) {
			for (String path : inputPath.split(";")) {

				final Path root = project.getFile().toPath().resolve(Paths.get(path));
				LOG.info("Path = " + root.toAbsolutePath());
				String fileExt = getFileExtension();
				String globPattern = "glob:{**/,}*." + fileExt;
				LOG.debug("Glob = " + globPattern);

				final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

				Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (matcher.matches(file) && !file.getFileName().toString().startsWith("_")) {
							fileCount.incrementAndGet();
							if (!processFile(root, file)) {
								errorCount.incrementAndGet();
							}
						}

						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
				});
			}
		}
		LOG.info("Compiled " + fileCount + " files");
		if (errorCount.get() > 0) {
			if (failOnError) {
				throw new Exception("Failed with " + errorCount.get() + " errors");
			} else {
				LOG.error("Failed with " + errorCount.get() + " errors. Continuing due to failOnError=false.");
			}
		}
	}

	protected boolean processFile(Path inputRootPath, Path inputFilePath) throws IOException {
		getLog().debug("Processing File " + inputFilePath);

		Path relativeInputPath = inputRootPath.relativize(inputFilePath);

		Path outputRootPath = this.outputPath.toPath();
		Path outputFilePath = outputRootPath.resolve(relativeInputPath);
		String fileExtension = getFileExtension();
		outputFilePath = Paths
				.get(outputFilePath.toAbsolutePath().toString().replaceFirst("\\." + fileExtension + "$", ".css"));

		/*Path sourceMapRootPath = Paths.get(this.sourceMapOutputPath);
		Path sourceMapOutputPath = sourceMapRootPath.resolve(relativeInputPath);
		sourceMapOutputPath = Paths
				.get(sourceMapOutputPath.toAbsolutePath().toString().replaceFirst("\\.scss$", ".css.map"));

		
		 * if (copySourceToOutput) { Path inputOutputPath =
		 * outputRootPath.resolve(relativeInputPath); inputOutputPath.toFile().mkdirs();
		 * Files.copy(inputFilePath, inputOutputPath, REPLACE_EXISTING);
		 * buildContext.refresh(inputOutputPath.toFile()); inputFilePath =
		 * inputOutputPath; }
		 */

		Output out;
		try {
			out = compiler.compileFile(inputFilePath.toAbsolutePath().toString(),
					outputFilePath.toAbsolutePath().toString(), null);
		} catch (CompilationException e) {
			getLog().error(e.getMessage());
			getLog().debug(e);

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
					} catch (Exception e1) {
						getLog().error("Failed to parse error json line: " + e1.getMessage());
						getLog().debug(e1);
					}
				}
				Matcher columnMatcher = PATTERN_ERROR_JSON_COLUMN.matcher(errorJson);
				if (columnMatcher.find()) {
					try {
						column = Integer.parseInt(columnMatcher.group(1));
						// in case regex doesn't cut it anymore
					} catch (Exception e1) {
						getLog().error("Failed to parse error json column: " + e1.getMessage());
						getLog().debug(e1);
					}
				}
			}

			return false;
		}

		LOG.info("Compilation finished.");

		writeContentToFile(outputFilePath, out.getCss());
		if (out.getSourceMap() != null) {
			//writeContentToFile(sourceMapOutputPath, out.getSourceMap());
		}
		return true;
	}

	private void writeContentToFile(Path outputFilePath, String content) throws IOException {
		File f = outputFilePath.toFile();
		f.getParentFile().mkdirs();
		f.createNewFile();
		OutputStreamWriter os = null;
		try {
			os = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
			os.write(content);
			os.flush();
		} finally {
			if (os != null) {
				os.close();
			}
		}
		LOG.info("Written to: " + f);
	}

}
