/*

API notes:

  GET /tracers
    returns list of current tracers
  POST /tracer?class=...&method=...&kind=...


- handle logs
- handle deletions
- ui for decompile
- make sure you can't overwrite multiple times

*/

(function() {
  var self = this;

  // self.activeTab = ko.observable("home");
  self.activeTab = ko.observable("tracer");
  self.tabs = ko.observable([
    "home",
    "jmx",
    "threads",
    "profiler",
    "tracer",
    "classes",
    "decompile",
    "evaluate"]);

  self.tracer = {
    'refresh': function() {
        $.get("/tracer/list")
         .done(function(d) {
           self.tracer.traces($.parseJSON(d));
        });
        $.post("/tracer/log", { }, function(d) { 
          // No doubt could be done better.
          self.tracer.log(self.tracer.log() + $.parseJSON(d));
        });
    },
    'delete': function(d) {
      $.post("/tracer/remove", { 'id': d.id }, function() { self.tracer.refresh(); })
    },
    'log': ko.observable(""),
    'class': ko.observable("Noop"),
    'method': ko.observable("doNothing"),
    'install': function() {
       $.post("/tracer/add",
          { 
            'klass': self.tracer.class(),
            'method': self.tracer.method(),
            'hook': self.tracer.code(),
            'hookName': "injected.CustomHook"
          },
          function() { self.tracer.refresh(); })
    },
    'traces': ko.observableArray(),
    'code': ko.observable(
            "package injected;\n" +
            "import java.io.PrintWriter;\n" +
            "\n" +
            "import org.cloudera.stethoscope.Hook;\n" +
            "\n" +
            "public class CustomHook implements Hook {\n" +
            "  private Hook.Log log;\n" +
            "\n" +
            "  @Override\n" +
            "  public void begin(Object[] args) {\n" +
            "    // todo\n" +
            "    log.log(\"Invocation!\");\n" +
            "  }\n" +
            "\n" +
            "  @Override\n" +
            "  public void end() {\n" +
            "    // todo\n" +
            "  }\n" +
            "  @Override\n" +
            "  public void setLog(Hook.Log log) {\n" +
            "    this.log = log;\n" +
            "  }\n" +
            "\n" +
            "  @Override\n" +
            "  public String toString() {\n" +
            "    return \"hook\";\n" +
            "  }\n" +
            "}\n")
  };

  self.decompile = {
    'class': ko.observable("java.lang.String"),
    'decompiled': ko.observable("Choose\na\nclass\n"),
    'run': function() {
      $.get("/tracer/decompile",
        { 'klass': self.decompile.class() })
       .done(function(d) {
        self.decompile.decompiled($.parseJSON(d));
       });
    }
  }

  self.activate = function(newTab) {
    self.activeTab(newTab);
  };

  self.classes = ko.observableArray();

  self.spinner = ko.observable(true);

  self.jmx = {
    'data': ko.observable("Loading...")
  };

  self.threads = {
    'data': ko.observable("Loading...")
  };

  self.tabActivations = {
    'jmx': function() {
      $.get('/jmx')
       .done(function(d) { self.jmx.data(JSON.stringify(d, null, "  ")); });
    },
    'threads': function() {
      $.get('/json/threads')
       .done(function(d) { self.threads.data($.parseJSON(d)); });
    },
    'tracer': function() {
      self.tracer.refresh();
      self.tracer.interval = setInterval(self.tracer.refresh, 10000);
    },
    'classes': function() {
      self.spinner(true);
      $.get('/json/loadedClasses')
      .done(function(d) {
        if(false) {
          self.classes.removeAll();
          ko.utils.arrayPushAll(self.classes(), d);
          self.classes.valueHasMutated();
          // self.classes(d);
        }
      })
      .fail(function(d) { alert("err"); })
      .always(function() {
          self.spinner(false);
      });
    }
  };

  self.activeTab.subscribe(function(newtab) {
    var f = self.tabActivations[newtab];
    if (f) {
      f();
    }
  });

  // Bind ourselves to the everything!
  ko.applyBindings(self, $("body")[0]);
}());
