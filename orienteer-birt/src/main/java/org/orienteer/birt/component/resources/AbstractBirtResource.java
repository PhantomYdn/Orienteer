package org.orienteer.birt.component.resources;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.wicket.request.resource.AbstractResource;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.IRenderOption;
import org.eclipse.birt.report.engine.api.IRenderTask;
import org.eclipse.birt.report.engine.api.IReportDocument;
import org.orienteer.birt.component.AbstractBirtReportPanel;

/**
 * Base resource for BIRT reports export
 *
 */
public abstract class AbstractBirtResource extends AbstractResource {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private AbstractBirtReportPanel reportPanel;

	public AbstractBirtResource(AbstractBirtReportPanel reportPanel) {
		this.reportPanel = reportPanel;
	}

	@Override
	protected ResourceResponse newResourceResponse(Attributes attributes) {
	    final ResourceResponse resourceResponse = new ResourceResponse();
	    setResourceData(resourceResponse);
	    
	    resourceResponse.setWriteCallback(new WriteCallback()
	    {
	      @Override
	      public void writeData(Attributes attributes) throws  IOException
	      {
	  		IReportDocument cache;
			try {
				cache = reportPanel.getReportCache();
				IRenderTask renderTask = reportPanel.getReportEngine().createRenderTask(cache);
				
		        OutputStream outputStream = attributes.getResponse().getOutputStream();
				renderTask.setRenderOption(getRenderOptions(outputStream));
				//run the report
				renderTask.render();
				cache.close();	        
			} catch (EngineException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	      }
	    });
	    return resourceResponse;
	}
	
	abstract protected IRenderOption getRenderOptions(OutputStream output);
	
	abstract protected void setResourceData(ResourceResponse resourceResponse);

}