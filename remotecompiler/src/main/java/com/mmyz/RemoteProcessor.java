package com.mmyz;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@AutoService(Processor.class)
public class RemoteProcessor extends AbstractProcessor {

    private Filer mFiler;
    private Messager mMessager;


    private List<String> mStaticRemoteUriList = new ArrayList<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mFiler = processingEnvironment.getFiler();
        mMessager = processingEnvironment.getMessager();
    }

    /**
     * java compiler version
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    /**
     * need handle Annotation type
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new HashSet<>();
        types.add(Modules.class.getCanonicalName());
        types.add(Module.class.getCanonicalName());
        types.add(StaticRemote.class.getCanonicalName());
        return types;
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment re) {
        mStaticRemoteUriList.clear();
        try {
            Set<? extends Element> modules = re.getElementsAnnotatedWith(Modules.class);
//            mMessager.printMessage(Diagnostic.Kind.ERROR,elementsAnnotatedWith.toString());
//            mMessager.printMessage(Diagnostic.Kind.NOTE,modules.toString());
//            mMessager.printMessage(Diagnostic.Kind.NOTE,module.toString());

            if (modules != null && !modules.isEmpty()){
                patchModulesClass(modules);
                return true;
            }
            processModule(re);

        }catch (Exception e){
            mMessager.printMessage(Diagnostic.Kind.NOTE,e.getMessage());
        }



        return true;
    }

    /**
     *
     * @param re
     */
    private void processModule(RoundEnvironment re) {

        try {

            Set<? extends Element> staticElementSet = re.getElementsAnnotatedWith(StaticRemote.class);
            if (staticElementSet != null && !staticElementSet.isEmpty()) {
                for (Element e :
                        staticElementSet) {
                    if (!(e instanceof TypeElement)) {
                        continue;
                    }
                    TypeElement te = (TypeElement) e;
                    mStaticRemoteUriList.add(te.getAnnotation(StaticRemote.class).value());
                }
            }

            Set<? extends Element> module = re.getElementsAnnotatedWith(Module.class);
//            mMessager.printMessage(Diagnostic.Kind.NOTE,module.toString());

            patchModuleClass(module);
        }catch (Exception e) {
            mMessager.printMessage(Diagnostic.Kind.NOTE,e.getMessage());
        }


    }

    /**
     * create class
     *
     *  package com.mmyz.router;
     *
     *  public class Page_Login(){
     *      public static autoInvoke(){
     *          Remote.putRemoteUriDefaultPattern("activity://com.mmyz.account.LoginActivity");
     *      }
     *  }
     *
     */
    private void patchModuleClass(Set<? extends Element> module) {

        try {
            if (module == null || module.isEmpty())
                return;

            mMessager.printMessage(Diagnostic.Kind.NOTE,module.toString());
            Element next = module.iterator().next();
            Module annotation = next.getAnnotation(Module.class);

            String pageName = annotation.value();
            String className = IRemoteConfig.PAGE_PREFIX+pageName;

            JavaFileObject file = mFiler.createSourceFile(className, next);

//            mMessager.printMessage(Diagnostic.Kind.NOTE,module.toString());
//            mMessager.printMessage(Diagnostic.Kind.NOTE,file.toString());
//            mMessager.printMessage(Diagnostic.Kind.NOTE,next.toString());
//            mMessager.printMessage(Diagnostic.Kind.NOTE,pageName);
//            mMessager.printMessage(Diagnostic.Kind.NOTE,className);

            PrintWriter printWriter = new PrintWriter(file.openWriter());
            printWriter.println("package "+IRemoteConfig.PACKAGE_NAME +";");
            printWriter.println("import "+IRemoteConfig.PACKAGE_NAME+".Remote;");
            printWriter.println("import "+IRemoteConfig.PACKAGE_NAME+".exception.NotFoundClassException;");
            printWriter.println("public class "+className +" {");
            printWriter.println("public static void "+IRemoteConfig.PAGE_METHOD_NAME+"(){");

            printWriter.println("try{");
            for (String uri :
                    mStaticRemoteUriList) {
                printWriter.println("Remote.putRemoteUriDefaultPattern(\""+uri+"\");");
            }
            printWriter.println("}catch(NotFoundClassException e){");
            printWriter.println("e.printStackTrace();");
            printWriter.println("}");
            printWriter.println("}");
            printWriter.println("}");
            printWriter.flush();
            printWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Create class
     *
     *    package com.mmyz.remote;
     *    public class AutoRegisterRemote{
     *        public void autoRegister(){
     *            Page_Login.autoInvoke();
     *        }
     *    }
     */
    private void patchModulesClass(Set<? extends Element> modules) {

        try {
            TypeElement moduleTypeElement= (TypeElement) modules.iterator().next();
            JavaFileObject file = mFiler.createSourceFile(IRemoteConfig.CLASS_NAME, moduleTypeElement);
            PrintWriter writer = new PrintWriter(file.openWriter());
            writer.println("package "+IRemoteConfig.PACKAGE_NAME+";");
            writer.println("public class "+IRemoteConfig.CLASS_NAME +" {");
            writer.println("public static void "+IRemoteConfig.METHOD_NAME +" () {");
            Modules modulesAnnotation = moduleTypeElement.getAnnotation(Modules.class);
            String[] value = modulesAnnotation.value();

//            writer.println(" System.out.println(\"===========测试反射调用===\");");
            for (String item :
                    value) {
                writer.println(IRemoteConfig.PACKAGE_NAME+"."+IRemoteConfig.PAGE_PREFIX+item+"."+IRemoteConfig.PAGE_METHOD_NAME +"();");
            }
            writer.println("}");
            writer.println("}");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            mMessager.printMessage(Diagnostic.Kind.ERROR,e.getMessage());
        }
    }
}
