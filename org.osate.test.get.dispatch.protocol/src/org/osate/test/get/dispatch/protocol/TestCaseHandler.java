package org.osate.test.get.dispatch.protocol;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.osate.aadl2.AadlPackage;
import org.osate.aadl2.ComponentClassifier;
import org.osate.aadl2.ComponentType;
import org.osate.aadl2.Element;
import org.osate.aadl2.EnumerationLiteral;
import org.osate.aadl2.ModelUnit;
import org.osate.aadl2.SystemImplementation;
import org.osate.aadl2.ThreadClassifier;
import org.osate.aadl2.instance.ComponentInstance;
import org.osate.aadl2.instance.SystemInstance;
import org.osate.aadl2.instance.util.InstanceSwitch;
import org.osate.aadl2.instantiation.InstantiateModel;
import org.osate.aadl2.modelsupport.util.AadlUtil;
import org.osate.ui.dialogs.Dialog;
import org.osate.xtext.aadl2.properties.util.GetProperties;

public class TestCaseHandler extends AbstractHandler
  {
  public static String SOURCE_FILE_EXT = "aadl";

  private static SystemInstance si = null;

  class Walker extends InstanceSwitch<Boolean>
    {

    public void showDispatchProtocols(SystemInstance si)
      {
      this.doSwitch(si);
      }

    @Override
    public Boolean caseComponentInstance(ComponentInstance ci)
      {
      ComponentClassifier cc = ci.getClassifier();
      if (cc instanceof ThreadClassifier)
        {
        EnumerationLiteral dispatchProtocol = GetProperties.getDispatchProtocol(cc);
        if (dispatchProtocol == null)
          writeToConsole("Thread " + cc.getName() + " has null Dispatch_Prototcol.");
        else
          writeToConsole("Thread " + cc.getName() + " has Dispatch_Prototcol => " + dispatchProtocol.getName());
        }
      for (ComponentInstance child : ci.getComponentInstances())
        {
        this.doSwitch(child);
        }
      return true;
      }
    }

  public IStatus runJob(Element elem, IProgressMonitor monitor)
    {

//MessageConsole console = displayConsole();
//console.clearConsole();
    si = getSystemInstance(elem);
    if (si == null)
      {
      Dialog.showError(getToolName(), "Please select a system implementation or a system instance");
      return Status.CANCEL_STATUS;
      }
//    refreshWorkspace();
    writeToConsole("try using instance model");

    Walker w = new Walker();
    w.showDispatchProtocols(si);
    
    writeToConsole("try using workspace");
    tryWorkspace();

    return Status.OK_STATUS;

    }

  private void tryWorkspace()
    {
    HashSet<IFile> files = getAadlFilesInWorkspace();
    for (IFile file : files)
      {
      ModelUnit ir = (ModelUnit) AadlUtil.getElement(file);
      if (ir instanceof AadlPackage)
        {
        List<ComponentType> componentTypesInPackage = EcoreUtil2.eAllOfType((AadlPackage)ir, ComponentType.class);
        for (ComponentType ct : componentTypesInPackage)
          if (ct instanceof ThreadClassifier)
            {
            EnumerationLiteral dispatchProtocol = GetProperties.getDispatchProtocol(ct);
            if (dispatchProtocol == null)
              writeToConsole("Thread " + ct.getName() + " has null Dispatch_Prototcol.");
            else
              writeToConsole("Thread " + ct.getName() + " has Dispatch_Prototcol => " + dispatchProtocol.getName());
            }       
        }
      }
    }


  
  protected String getToolName()
    {
    return "TestGetDispatchProtocol";
    }

  protected String getJobName()
    {
    return getToolName() + " job";
    }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException
    {
    System.out.println("testing GetProperties.getDispatchProtocol");

    Element      elem = getElement(event);

    WorkspaceJob j    = new WorkspaceJob(getJobName())
                        {
                        @Override
                        public IStatus runInWorkspace(final IProgressMonitor monitor)
                          {
                          return runJob(elem, monitor);
                          }
                        };

    j.setRule(ResourcesPlugin.getWorkspace().getRoot());
    j.schedule();
    return null;
    }

  private Element getElement(ExecutionEvent e)
    {
    Element root = AadlUtil.getElement(getCurrentSelection(e));

    if (root == null)
      {
      root = SelectionHelper.getSelectedSystemImplementation();
      }

    return root;
    }

  protected boolean writeToConsole(String text)
    {
    return writeToConsole(text, false);
    }

  protected boolean writeToConsole(String text, boolean clearConsole)
    {
    MessageConsole ms = displayConsole(getToolName());
    if (clearConsole)
      {
      ms.clearConsole();
      }
    return writeToConsole(ms, text);
    }

  protected boolean writeToConsole(MessageConsole m, String text)
    {
    boolean isWritten = false;
    if (m != null)
      {
      MessageConsoleStream out = m.newMessageStream();
      out.println(text);
      isWritten = true;
      try
        {
        out.flush();
        out.close();
        }
      catch (IOException e)
        {
        e.printStackTrace();
        }
      }
    return isWritten;
    }

  protected MessageConsole displayConsole()
    {
    return displayConsole(getToolName());
    }

  protected MessageConsole displayConsole(String name)
    {
    MessageConsole ms = getConsole(name);
    Display.getDefault().syncExec(() ->
      {
      try
        {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IConsoleView   view;
        view = (IConsoleView) page.showView(IConsoleConstants.ID_CONSOLE_VIEW);
        view.display(ms);
        }
      catch (PartInitException e)
        {
        e.printStackTrace();
        }
      });
    return ms;
    }

  private MessageConsole getConsole(String name)
    {
    ConsolePlugin   plugin   = ConsolePlugin.getDefault();
    IConsoleManager conMan   = plugin.getConsoleManager();
    IConsole[]      existing = conMan.getConsoles();
    for (int i = 0; i < existing.length; i++)
      {
      if (name.equals(existing[i].getName()))
        {
        return (MessageConsole) existing[i];
        }
      }
// no console found, so create a new one
    MessageConsole mc = new MessageConsole(name, null);
    conMan.addConsoles(new IConsole[]
      { mc });

    return mc;
    }

  protected SystemInstance getSystemInstance(Element e)
    {
    if (e != null)
      {
      if (e instanceof SystemInstance)
        {
        return (SystemInstance) e;
        }
      if (e instanceof SystemImplementation)
        {
        try
          {
          SystemImplementation si = (SystemImplementation) e;

          writeToConsole("Generating System Instance ...");

          return InstantiateModel.buildInstanceModelFile(si);
          }
        catch (Exception ex)
          {
          Dialog.showError(getToolName(), "Could not instantiate model");
          ex.printStackTrace();
          }
        }
      }
    return null;
    }

  public static SystemInstance getSystemInstance()
    {
    return si;
    }

  protected void refreshWorkspace()
    {
    try
      {
      ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
      }
    catch (CoreException e)
      {
      e.printStackTrace();
      }
    }
  
  protected Object getCurrentSelection(ExecutionEvent event) {
  ISelection selection = HandlerUtil.getCurrentSelection(event);
  if (selection instanceof IStructuredSelection && ((IStructuredSelection) selection).size() == 1) {
    Object object = ((IStructuredSelection) selection).getFirstElement();
    return object;
  } else {
    return null;
  }
}

  public static HashSet<IFile> 
getAadlFilesInWorkspace() {
  HashSet<IFile> result = new HashSet<IFile>();
  getFiles(getProjects(), result, SOURCE_FILE_EXT);
  return result;
}
  
  
  private static IProject[] 
getProjects() {
  IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

  IProject[] tmp = new IProject[projects.length];

  int cn = 0;
  for (int i = 0, max = projects.length; i < max; i++) {
    IProject project = projects[i];
    if (project.isOpen()) // || projectIsContributedByBless(project)) 
      tmp[cn++] = project;
  }
  return tmp;
}
  
  private static HashSet<IFile> 
getFiles(IResource[] resources, HashSet<IFile> result, String extension) {
  try 
    {
    for (int i = 0; i < resources.length; i++) 
      {
      if (resources[i] instanceof IFile) 
        {
        IFile file = (IFile) resources[i];
        String ext = file.getFileExtension();
        if (ext != null) 
          if (extension.equalsIgnoreCase(SOURCE_FILE_EXT)
              && ext.equalsIgnoreCase(SOURCE_FILE_EXT)) 
            result.add((IFile) resources[i]);
        }
      else if (resources[i] instanceof IContainer) 
        {
        IContainer cont = (IContainer) resources[i];
        if (!cont.getName().startsWith(".")) 
          getFiles(cont.members(), result, extension);
        }
      }
    } catch (CoreException e) {
//    log(e);
    }
  return result;
  }

  
}  
