/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.imeji.collection;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import de.mpg.imeji.beans.Navigation;
import de.mpg.imeji.beans.SessionBean;
import de.mpg.imeji.facet.FacetsBean;
import de.mpg.imeji.image.ImagesBean;
import de.mpg.imeji.image.ThumbnailBean;
import de.mpg.imeji.search.URLQueryTransformer;
import de.mpg.imeji.util.BeanHelper;
import de.mpg.imeji.util.ImejiFactory;
import de.mpg.imeji.util.ObjectLoader;
import de.mpg.jena.controller.CollectionController;
import de.mpg.jena.controller.ImageController;
import de.mpg.jena.controller.SearchCriterion;
import de.mpg.jena.controller.SearchCriterion.ImejiNamespaces;
import de.mpg.jena.controller.SortCriterion;
import de.mpg.jena.controller.SortCriterion.SortOrder;
import de.mpg.jena.export.ExportManager;
import de.mpg.jena.search.SearchResult;
import de.mpg.jena.security.Operations.OperationsType;
import de.mpg.jena.security.Security;
import de.mpg.jena.util.ObjectHelper;
import de.mpg.jena.vo.CollectionImeji;
import de.mpg.jena.vo.Image;

public class CollectionImagesBean extends ImagesBean  implements Serializable
{
	private int totalNumberOfRecords;
	private String id = null;
	private URI uri;
	private SessionBean sb = null;
	private CollectionImeji collection;
	private Navigation navigation;
	private List<SearchCriterion> scList = new ArrayList<SearchCriterion>();

	public CollectionImagesBean() 
	{
		super();
		sb = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);
		this.navigation = (Navigation) BeanHelper.getApplicationBean(Navigation.class);
	} 

	public void init() 
	{
		collection = ObjectLoader.loadCollection(ObjectHelper.getURI(CollectionImeji.class, id), sb.getUser());

		List<SelectItem> sortMenu = new ArrayList<SelectItem>();
		sortMenu.add(new SelectItem(ImejiNamespaces.PROPERTIES_CREATION_DATE, sb.getLabel(ImejiNamespaces.PROPERTIES_CREATION_DATE.name())));
		sortMenu.add(new SelectItem(ImejiNamespaces.PROPERTIES_LAST_MODIFICATION_DATE, sb.getLabel(ImejiNamespaces.PROPERTIES_LAST_MODIFICATION_DATE.name())));
		setSortMenu(sortMenu);
	}

	@Override
	public String getNavigationString() 
	{
		if (collection != null)
		{
			if(sb.getSelectedImagesContext()!=null && !(sb.getSelectedImagesContext().equals("pretty:collectionImages" + collection.getId().toString())))
				sb.getSelected().clear();
			sb.setSelectedImagesContext("pretty:collectionImages" + collection.getId().toString());
		}
		return "pretty:collectionImages";
	}

	@Override
	public int getTotalNumberOfRecords() {
		return totalNumberOfRecords;
	}

	@Override
	public List<ThumbnailBean> retrieveList(int offset, int limit) 
	{	
		if (getFacets() != null)
		{
			getFacets().getFacets().clear();
		}

		SortCriterion sortCriterion = new SortCriterion();
		sortCriterion.setSortingCriterion(ImejiNamespaces.valueOf(getSelectedSortCriterion()));
		sortCriterion.setSortOrder(SortOrder.valueOf(getSelectedSortOrder()));		

		initBackPage();

		try {
			scList = URLQueryTransformer.transform2SCList(getQuery());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		uri = ObjectHelper.getURI(CollectionImeji.class, id);

		SearchResult results = search(scList, sortCriterion);
		totalNumberOfRecords = results.getNumberOfRecords();
		results.setQuery(getQuery());
		results.setSort(sortCriterion);
				
		return ImejiFactory.imageListToThumbList(loadImages(results));
	}

	public SearchResult search(List<SearchCriterion> scList, SortCriterion sortCriterion)
	{
		ImageController controller = new ImageController(sb.getUser());
		return  controller.searchImagesInContainer(uri, scList, sortCriterion, getElementsPerPage(), getOffset());
	}

	public String getImageBaseUrl()
	{
		if (collection == null)
		{
			return "";
		}
		return navigation.getApplicationUri() + collection.getId().getPath();
	}

	@Override
	public String initFacets() throws Exception
	{
		scList = URLQueryTransformer.transform2SCList(getQuery());
		setFacets(new FacetsBean(collection, scList));
		return "pretty";
	}

	public String getBackUrl() {
		return navigation.getImagesUrl() + "/collection" + "/" + this.id;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setCollection(CollectionImeji collection) {
		this.collection = collection;
	}

	public CollectionImeji getCollection() {
		return collection;
	}

	public String release()
	{
		CollectionController cc = new CollectionController(sb.getUser());

		try 
		{
			cc.release(collection);
			BeanHelper.info(sb.getMessage("success_collection_release"));
		} 
		catch (Exception e) 
		{
			BeanHelper.error(sb.getMessage("error_collection_release"));
			BeanHelper.error(e.getMessage());
			e.printStackTrace();
		}

		return "pretty:";
	}

	public String delete()
	{
		CollectionController cc = new CollectionController(sb.getUser());

		try 
		{
			cc.delete(collection, sb.getUser());
			BeanHelper.info(sb.getMessage("success_collection_delete"));
		} 
		catch (Exception e) 
		{
			BeanHelper.error(sb.getMessage("success_collection_delete"));
			BeanHelper.error(e.getMessage());
			e.printStackTrace();
		}

		return "pretty:collections";
	}

	public String withdraw() throws Exception
	{
		CollectionController cc = new CollectionController(sb.getUser());

		try 
		{
			cc.withdraw(collection);
			BeanHelper.info(sb.getMessage("success_collection_withdraw"));
		} 
		catch (Exception e) 
		{
			BeanHelper.error(sb.getMessage("error_collection_withdraw"));
			BeanHelper.error(e.getMessage());
			e.printStackTrace();
		}

		return "pretty:";
	}

	public boolean isEditable()
	{
		Security security = new Security();
		return security.check(OperationsType.UPDATE, sb.getUser(), collection);
	}

	/**
	 * Check that at leat one image is editable
	 */
	//	public boolean isImageEditable()
	//	{
	//		for (ThumbnailBean tb : getCurrentPartList())
	//		{
	//			if (tb.isEditable())
	//			{
	//				return true;
	//			}
	//		}
	//    	return false;
	//	}
	//	
	//	public boolean isImageDeletable()
	//	{
	//		for (ThumbnailBean tb : getCurrentPartList())
	//		{
	//			if (tb.isDeletable())
	//			{
	//				return true;
	//			}
	//		}
	//    	return false;
	//	}

	public boolean isVisible() 
	{
		Security security = new Security();
		return security.check(OperationsType.READ, sb.getUser(), collection);
	}

	public boolean isDeletable() 
	{
		Security security = new Security();
		return security.check(OperationsType.DELETE, sb.getUser(), collection);
	}
}
