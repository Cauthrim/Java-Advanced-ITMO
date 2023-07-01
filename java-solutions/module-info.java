module info.kgeorgiy.ja.karpov {
	requires junit;
	requires java.management.rmi;
	requires java.compiler;

	requires info.kgeorgiy.java.advanced.base;
	requires info.kgeorgiy.java.advanced.walk;
	requires info.kgeorgiy.java.advanced.arrayset;
	requires info.kgeorgiy.java.advanced.student;
	requires info.kgeorgiy.java.advanced.implementor;
	requires info.kgeorgiy.java.advanced.concurrent;
	requires info.kgeorgiy.java.advanced.crawler;
	requires info.kgeorgiy.java.advanced.hello;

	exports info.kgeorgiy.ja.karpov.walk;
    opens info.kgeorgiy.ja.karpov.walk;
    exports info.kgeorgiy.ja.karpov.arrayset;
    opens info.kgeorgiy.ja.karpov.arrayset;
    exports info.kgeorgiy.ja.karpov.student;
    opens info.kgeorgiy.ja.karpov.student;
    exports info.kgeorgiy.ja.karpov.implementor;
    opens info.kgeorgiy.ja.karpov.implementor;
    exports info.kgeorgiy.ja.karpov.concurrent;
    opens info.kgeorgiy.ja.karpov.concurrent;
    exports info.kgeorgiy.ja.karpov.crawler;
    opens info.kgeorgiy.ja.karpov.crawler;
    exports info.kgeorgiy.ja.karpov.hello;
    opens info.kgeorgiy.ja.karpov.hello;
}