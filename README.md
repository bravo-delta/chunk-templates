Chunk Templates for Java
========================

Full documentation at http://www.x5software.com/chunk/

Chunk is a Java Template Engine for applications that serve HTML or XML.  It's the little engine that can handle any templating job, plain text, markup, markdown, whatever, wherever.

Chunk is compact and speedy, easy to learn yet very powerful.

Chunk templates provide a rich featureset: in-tag filters, in-tag default values, includes, looping and branching, defining multiple snippets per template file, layered themes, macros, and much much more.

Quick Start
===========

Browse the [fabulous documentation][5] chock full of examples and recipes.

[Download the latest chunk-template jar][2]. Works with Java 1.5 and up.

Available from Maven Central:

```
    <dependency>
      <groupId>com.x5dev</groupId>
      <artifactId>chunk-templates</artifactId>
      <version>2.5</version>
    </dependency>
```

Features
========
  * Nestable loops and simple conditionals (if/elseIf/else).
  * Macros, Includes and Conditional Includes.
  * Speedy rendering engine that pre-parses templates.
  * Curly-brace {$tags} pop nicely in a backdrop full of ```<AngleBrackets>``` (and your xml/html can still validate).
  * Flexible null-handling; template designer may specify default tag values.
  * Library of powerful chainable [filters][1] a la {$tag|trim}, including regex (regular expressions), sprintf.
  * Localization framework.
  * Rapid MVC: Glue a "model" object (or objects) to a "view" template with a single line of controller code.
  * Define multiple snippets per template file.
  * Stateless tags - encourages cleaner code via separation of concerns.
  * Support for theme layers with layered inheritance.
  * Hooks for extending.
  * Eclipse Template Editor plugin available with syntax highlighting & more.

An [Eclipse plugin][2] with syntax highlighting, outline navigation of snippets, and auto-linking of snippet references is available on the [Chunk project downloads page][2].  Requires Eclipse Helios (3.6) or better.  [Plugin installation instructions][3].

----

Android: Binding Beans to Template
==================================

Note: on Android (optional) - to make use of chunk.setToBean("tag",bean) binding, just make sure to include this additional dependency in your project:
```
    <dependency>
      <groupId>com.madrobot</groupId>
      <artifactId>madrobotbeans</artifactId>
      <version>0.1</version>
    </dependency>
```

Or download madrobotbeans-0.1.jar from the [Downloads][2] area.  Thanks to Elton Kent and the [Mad Robot][4] project.

  [1]: http://www.x5software.com/chunk/wiki/Chunk_Tag_Filters
  [2]: http://code.google.com/p/chunk-templates/downloads/list
  [3]: http://www.x5software.com/chunk/wiki/index.php/Eclipse_Template_Editor_plugin
  [4]: https://code.google.com/p/mad-robot/
  [5]: http://www.x5software.com/chunk/