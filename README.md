# Stethoscope: web-based, runtime Java debugging
## I'm in your maching, changing your bytecode!

# Overview

Attach Stethoscope to your existing Java process
with (as the same user as that Java process):

```
java -jar stethoscope.jar <pid> [[host]:port]
```

Then visit the Stethoscope UI with your
web browser.  The default URL is http://localhost:1234/ .

# How it works

Stethoscope attaches to an existing
JVM using the [Virtual Machine API](https://docs.oracle.com/javase/7/docs/jdk/api/attach/spec/com/sun/tools/attach/VirtualMachine.html), and then
adds itself as a [Java agent](https://docs.oracle.com/javase/6/docs/api/java/lang/instrument/package-summary.html).  The agent, now running inside of
the target JVM, starts a web server,
and voila.

The runtime Hooks are added by using the
[Instrumentation.retransformClasses](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/Instrumentation.html#retransformClasses(java.lang.Class...)) mechanism.  Roughly, Java lets you re-write a Java
class at runtime: you are given the `byte[]`
representation of the bytecode, and, using
[ASM](http://asm.ow2.org/), I add in the
hooks.  (ASM gives us a nice visitor pattern
for the bytecode, as well as ample documentation.)

# But why?

Java provides ample capabilities for
debugging, and you've no doubt typed
`-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=4000,suspend=n` so many times that you've
memorised it.  When it works, it's fabulous,
but it requires knowing that you're going to
debug the system ahead of time (to add the flags)
and having enough Eclipse/IntelliJ/whatever
set up to meaningfully set breakpoints and what-not.

Meanwhile, what I'm often doing with a debugger is
boring stuff: looking at the arguments to some method,
say, or seeing if a certain bit of code is triggered.
And often I'm recompiling and restarting code to add
some instrumentation to see how long some operation
takes.

Stethoscope lets you poke at a system that's
already running, and it does so with a light
install (one jar) and a familiar UI: the web.
That last bit is not to be underestimated:
one person's "common knowledge" is another's
slog through fifteen man pages.  Why would
you have the arguments to `jstat` memorized?

# Related Stuff

* [CRaSH shell](http://www.crashub.org/). Commandline tool for common tools.  Lets you run Groovy code inside target JVM.  Crashed (no pun intended) a few times in my experience.
* [Swiss Java Knife &em; aragozin/jvm-tools](https://github.com/aragozin/jvm-tools) Handy collection of tools, including "thread top."
* dtrace
* btrace
* http://stackoverflow.com/questions/18567552/how-to-retransform-a-class-at-runtime

# Reading

* On ASM:
  * http://download.forge.objectweb.org/asm/asm4-guide.pdf
  * "Using the ASM framework to implement common Java bytecode transformation patterns" http://asm.ow2.org/current/asm-transformations.pdf
* Java bytecode
  * http://en.wikipedia.org/wiki/Java_bytecode_instruction_listings


# Development

Build with

```
$ mvn package
```

Run against itself (handy for development) with

```
java -jar target/stethoscope-1.0-SNAPSHOT.jar self
```

Format `pom.xml` with `xmlstarlet format --indent-spaces 2`
