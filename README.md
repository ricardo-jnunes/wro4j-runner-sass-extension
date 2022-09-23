# wro4j-runner-sass-extension
SCSS Compiler for wro4j-runner

Entirely based on the [Libsass Maven Plugin](https://gitlab.com/haynes/libsalss-maven-plugin) that uses libsass to compile sass files to run with [wro4j-runner](https://github.com/wro4j/wro4j-runner)



How to use
============

- Updated to support ECMAScript6
   - `preProcessors=googleClosureEcma6`
- Customized to compiles SASS/SCSS
   - `preProcessors=scssCssImport`
   - `postProcessors=scssCssCompiler,yuiCSSCompressor`

New flags to use on Runner:

- groupParallel: Turns on the parallel group processing of resources. This value is false by default.
- cssOnly: When true, Ignore JS files on group parallel processing, useful when you do not spend time processing JS files on processors



Building
============
```
mvn clean install
```

Considerations
============

If you have any problems or understand that the content cannot be shared here, open an issue and I will resolve.

Thanks
============

Thank you very much 
https://raw.githubusercontent.com/wro4j/wro4j-runner