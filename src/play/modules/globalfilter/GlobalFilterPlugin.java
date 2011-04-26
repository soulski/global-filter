package play.modules.globalfilter;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClassloader;
import play.exceptions.JavaExecutionException;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.mvc.results.Result;
import play.vfs.VirtualFile;

public class GlobalFilterPlugin extends PlayPlugin {
	private static List<Class> filterClasses = new ArrayList();
	private static final String FOLDER_FILTER = Play.configuration.getProperty("global-filter.folder", "filter");

	private List<VirtualFile> readAllFilterClasses() {
		List filterFiles = new ArrayList();
		String filterRelativePath = File.separator + "app" + File.separator + FOLDER_FILTER;
		VirtualFile filterFolder = VirtualFile.fromRelativePath(filterRelativePath);
		if ((filterFolder.exists()) && (filterFolder.isDirectory())) {
			filterFiles = filterFolder.list();
		}
		return filterFiles;
	}

	public void onApplicationStart() {
		List<Class> sortFilterClasses = new ArrayList<Class>();
		List<VirtualFile> filterFiles = readAllFilterClasses();

		for (VirtualFile filterFile : filterFiles) {
			String filename = filterFile.getName();
			String classname = FOLDER_FILTER + "."
					+ filename.substring(0, filename.lastIndexOf("."));
			Class filterClass = Play.classloader.getClassIgnoreCase(classname);
			sortFilterClasses.add(filterClass);
		}

		Collections.sort(sortFilterClasses, new Comparator<Class>() {
			public int compare(Class clazz1, Class clazz2) {
				int priotityClazz1 = clazz1.isAnnotationPresent(Priority.class) ? ((Priority) clazz1
						.getAnnotation(Priority.class)).value() : 1;
				int priotityClazz2 = clazz2.isAnnotationPresent(Priority.class) ? ((Priority) clazz2
						.getAnnotation(Priority.class)).value() : 1;
				return priotityClazz2 - priotityClazz1;
			}
		});
		filterClasses = sortFilterClasses;
	}

	public void beforeActionInvocation(Method actionMethod) {
		Http.Request request = Http.Request.current();
		Http.Response response = Http.Response.current();
		for (Class filter : filterClasses)
			try {
				Method method = filter.getMethod("invoke", new Class[] {Http.Request.class, Http.Response.class});
				method.invoke(null, new Object[] { request, response });
			} catch (InvocationTargetException ex) {
				if(ex.getTargetException() instanceof Result) {
					throw (Result) ex.getTargetException();
				}
				
				throw new JavaExecutionException(Http.Request.current().action, ex);
			} catch (Exception e) {
				Logger.error(e, "Fail to run filter name : %s", new Object[] { filter.getName() });
			}
	}
}