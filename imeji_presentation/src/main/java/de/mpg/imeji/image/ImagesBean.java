/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.imeji.image;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;

import de.mpg.imeji.album.AlbumBean;
import de.mpg.imeji.beans.BasePaginatorListSessionBean;
import de.mpg.imeji.beans.Navigation;
import de.mpg.imeji.beans.SessionBean;
import de.mpg.imeji.facet.Facet;
import de.mpg.imeji.facet.Facet.FacetType;
import de.mpg.imeji.facet.FacetsBean;
import de.mpg.imeji.filter.Filter;
import de.mpg.imeji.filter.FiltersBean;
import de.mpg.imeji.filter.FiltersSession;
import de.mpg.imeji.history.HistorySession;
import de.mpg.imeji.search.URLQueryTransformer;
import de.mpg.imeji.util.BeanHelper;
import de.mpg.imeji.util.ImejiFactory;
import de.mpg.imeji.util.PropertyReader;
import de.mpg.jena.controller.AlbumController;
import de.mpg.jena.controller.CollectionController;
import de.mpg.jena.controller.ImageController;
import de.mpg.jena.controller.SearchCriterion;
import de.mpg.jena.controller.SearchCriterion.ImejiNamespaces;
import de.mpg.jena.controller.SortCriterion;
import de.mpg.jena.controller.SortCriterion.SortOrder;
import de.mpg.jena.search.SearchResult;
import de.mpg.jena.vo.CollectionImeji;
import de.mpg.jena.vo.Image;

public class ImagesBean extends BasePaginatorListSessionBean<ThumbnailBean> implements Serializable
{
	private int totalNumberOfRecords;
	private SessionBean sb;
	private List<SelectItem> sortMenu;
	private String selectedSortCriterion;
	private String selectedSortOrder;
	private FacetsBean facets;
	protected FiltersBean filters;
	private String query;
	private Navigation navigation;
	private Filter searchFilter;
	private boolean isSimpleSearch;
	
	private List<SearchCriterion> scList = new ArrayList<SearchCriterion>();

	private String discardComment;

	public ImagesBean()
	{
		super();
		sb = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);
		navigation = (Navigation)BeanHelper.getApplicationBean(Navigation.class);
		filters = new FiltersBean();
		initMenus();
		try 
		{
			setElementsPerPage(Integer.parseInt(PropertyReader.getProperty("imeji.image.list.size")));
		} 
		catch (Exception e) 
		{
			logger.error("Error loading property imeji.image.list.size", e);
		}
		try 
		{
			String options = PropertyReader.getProperty("imeji.image.list.size.options");
			for(String option : options.split(","))
			{
				getElementsPerPageSelectItems().add(new SelectItem(option));
			}
		} 
		catch (Exception e) 
		{
			logger.error("Error reading property imeji.image.list.size.options", e);
		}
	}

	public String getInitPage()
	{
		initMenus();
		
		if (facets != null)
		{
			facets.getFacets().clear(); 
		}
	
		initBackPage();

		try 
		{
			scList = URLQueryTransformer.transform2SCList(query);
		} 
		catch (Exception e) 
		{
			BeanHelper.error("Error parsing query");
			logger.error("Error parsing query", e);
		}
		
		for (Filter f : filters.getSession().getFilters())
		{
			if (FacetType.SEARCH.equals(f.getType()))
			{
				searchFilter = f;
			}
		}
		
		isSimpleSearch = URLQueryTransformer.isSimpleSearch(scList);
		
		return "";
	}

	private void initMenus()
	{
		sortMenu = new ArrayList<SelectItem>();
		sortMenu.add(new SelectItem(ImejiNamespaces.PROPERTIES_CREATION_DATE, sb.getLabel(ImejiNamespaces.PROPERTIES_CREATION_DATE.name())));
		sortMenu.add(new SelectItem(ImejiNamespaces.IMAGE_COLLECTION, sb.getLabel(ImejiNamespaces.IMAGE_COLLECTION.name())));
		sortMenu.add(new SelectItem(ImejiNamespaces.PROPERTIES_LAST_MODIFICATION_DATE, sb.getLabel(ImejiNamespaces.PROPERTIES_LAST_MODIFICATION_DATE.name())));

		selectedSortCriterion = ImejiNamespaces.PROPERTIES_CREATION_DATE.name();
		selectedSortOrder = SortOrder.DESCENDING.name();
	}

	@Override
	public String getNavigationString()
	{
		if(sb.getSelectedImagesContext()!=null && !(sb.getSelectedImagesContext().equals("pretty:images")))
		{
			sb.getSelected().clear();
		}
		sb.setSelectedImagesContext("pretty:images");
		return "pretty:images";
	}

	@Override
	public int getTotalNumberOfRecords()
	{
		return totalNumberOfRecords;
	}

	@Override
	public List<ThumbnailBean> retrieveList(int offset, int limit)
	{		
		SortCriterion sortCriterion = new SortCriterion();
		sortCriterion.setSortingCriterion(ImejiNamespaces.valueOf(getSelectedSortCriterion()));
		sortCriterion.setSortOrder(SortOrder.valueOf(getSelectedSortOrder()));
		
		SearchResult searchResult = search(scList, sortCriterion);
		searchResult.setQuery(query);
		searchResult.setSort(sortCriterion);

		totalNumberOfRecords = searchResult.getNumberOfRecords();
		
		// load images
		Collection<Image> images = loadImages(searchResult);

		return ImejiFactory.imageListToThumbList(images);
	}

	public SearchResult search(List<SearchCriterion> scList, SortCriterion sortCriterion)
	{
		ImageController controller = new ImageController(sb.getUser()); 
		return  controller.searchImages(scList, sortCriterion);
	}

	public Collection<Image> loadImages(SearchResult searchResult)
	{
		ImageController controller = new ImageController(sb.getUser()); 
		return controller.loadImages(searchResult.getResults(), getElementsPerPage(), getOffset());
	}

	public String getSimpleQuery()
	{
		if (searchFilter != null)
		{
			return URLQueryTransformer.transform2SimpleQuery(searchFilter.getScList());
		}
		return "";
	}
	
	public void initBackPage()
	{
		HistorySession hs = (HistorySession) BeanHelper.getSessionBean(HistorySession.class);
		FiltersSession fs = (FiltersSession) BeanHelper.getSessionBean(FiltersSession.class);

		if (FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("h") != null)
		{
			fs.setFilters(hs.getCurrentPage().getFilters());
			query = hs.getCurrentPage().getQuery();
		}
		else
		{
			filters = new FiltersBean(query, totalNumberOfRecords);
			hs.getCurrentPage().setFilters(fs.getFilters());
			hs.getCurrentPage().setQuery(fs.getWholeQuery());
		}
	}

	public String addToActiveAlbum() throws Exception
	{
		AlbumBean activeAlbum = sb.getActiveAlbum();
		AlbumController ac = new AlbumController(sb.getUser());

		int els = getElementsPerPage();
		int page = getCurrentPageNumber();

		setElementsPerPage(totalNumberOfRecords);
		setCurrentPageNumber(1);

		update();

		setElementsPerPage(els);
		setCurrentPageNumber(page);

		int count = 0;

		for (ThumbnailBean tb : getCurrentPartList())
		{
			if (tb.isInActiveAlbum())
			{
				BeanHelper.error(((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getLabel("image") + " " + tb.getFilename() + " " + ((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getMessage("already_in_active_album"));              		
			}
			else
			{
				activeAlbum.getAlbum().getImages().add(tb.getId());
				count++;
			}
		}

		try 
		{
			ac.update(activeAlbum.getAlbum());
			BeanHelper.info(count + " " + ((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getMessage("images_added_to_active_album"));       

		} 
		catch (Exception e) 
		{
			BeanHelper.error(e.getMessage());
			activeAlbum.setAlbum(ac.retrieve(activeAlbum.getAlbum().getId()));
			e.printStackTrace();
		}

		return "pretty:";
	}

	public String deleteAll() throws Exception 
	{
		ImageController ic = new ImageController(sb.getUser());
		CollectionController cc = new CollectionController(sb.getUser());
		CollectionImeji coll = null;

		SearchResult searchResult = search(scList, null);
		Collection<Image> images = loadImages(searchResult);

		if (images != null && !images.isEmpty()) coll = cc.retrieve(images.iterator().next().getCollection());
		int count = 0;
		for(Image im : images)
		{
			try 
			{
				ic.delete(im, sb.getUser());
				if (coll.getImages().contains(im.getId())) coll.getImages().remove(im.getId());
				count++;
			} 
			catch (Exception e) 
			{
				BeanHelper.error(sb.getMessage("error_image_delete") + " " + im.getFilename());
				e.printStackTrace();
			}
		}

		BeanHelper.info(count + " " + sb.getLabel("images_deleted"));
		cc.update(coll);
		sb.getSelected().clear();

		return "pretty:";
	}

	public String withdrawAll() throws Exception 
	{
		ImageController ic = new ImageController(sb.getUser());

		SearchResult searchResult = search(scList, null);
		Collection<Image> images = loadImages(searchResult);
		int count = 0;

		if ("".equals(discardComment.trim()))
		{
			BeanHelper.error(sb.getMessage("error_image_withdraw_discardComment"));
		}
		else
		{
			for(Image im : images)
			{
				try 
				{
					im.getProperties().setDiscardComment(discardComment);
					ic.withdraw(im);
					count++;
				} 
				catch (Exception e) 
				{
					BeanHelper.error(sb.getMessage("error_image_withdraw") + " " + im.getFilename());
					e.printStackTrace();
				}
			}
			discardComment = null;
			sb.getSelected().clear();
			BeanHelper.info(count + " " + sb.getLabel("images_withdraw"));
		}
		return "pretty:";
	}

	public String getImageBaseUrl()
	{
		return navigation.getApplicationUri();
	}

	public String getBackUrl() 
	{
		return navigation.getImagesUrl();
	}

	public String initFacets() throws Exception
	{
		scList =  URLQueryTransformer.transform2SCList(query);;
		this.setFacets(new FacetsBean(scList));
		return "pretty";
	}

	public List<SelectItem> getSortMenu()
	{
		return sortMenu;
	}

	public void setSortMenu(List<SelectItem> sortMenu)
	{
		this.sortMenu = sortMenu;
	}

	public String getSelectedSortCriterion()
	{
		return selectedSortCriterion;
	}

	public void setSelectedSortCriterion(String selectedSortCriterion)
	{
		this.selectedSortCriterion = selectedSortCriterion;
	}

	public String getSelectedSortOrder()
	{
		return selectedSortOrder;
	}

	public void setSelectedSortOrder(String selectedSortOrder)
	{
		this.selectedSortOrder = selectedSortOrder;
	}

	public String toggleSortOrder()
	{
		if (selectedSortOrder.equals("DESCENDING"))
		{
			selectedSortOrder = "ASCENDING";
		}
		else
		{
			selectedSortOrder = "DESCENDING";
		}
		return getNavigationString();
	}

	public FacetsBean getFacets()
	{
		return facets;
	}

	public void setFacets(FacetsBean facets)
	{
		this.facets = facets;
	}

	public void setQuery(String query)
	{
		this.query = query;
	}

	public String getQuery()
	{
		return query;
	}

	public FiltersBean getFilters() {
		return filters;
	}

	public void setFilters(FiltersBean filters) {
		this.filters = filters;
	}

	public String selectAll() 
	{
		for(ThumbnailBean bean: getCurrentPartList())
		{
			if(!(sb.getSelected().contains(bean.getId())))
			{
				sb.getSelected().add(bean.getId());
			}
		}
		return getNavigationString();
	}

	public String selectNone()
	{
		sb.getSelected().clear();
		return getNavigationString();
	}

	/**
	 * Check that at leat one image is editable
	 */
	public boolean isImageEditable()
	{
		for (ThumbnailBean tb : getCurrentPartList())
		{
			if (tb.isEditable())
			{
				return true;
			}
		}
		return false;
	}

	public boolean isImageDeletable()
	{
		for (ThumbnailBean tb : getCurrentPartList())
		{
			if (tb.isDeletable())
			{
				return true;
			}
		}
		return false;
	}

	public boolean isEditable()
	{
		return false;
	}

	public boolean isVisible() 
	{
		return false;
	}

	public boolean isDeletable() 
	{
		return false;
	}

	public String getDiscardComment() {
		return discardComment;
	}

	public void setDiscardComment(String discardComment) 
	{
		this.discardComment = discardComment;
	}

	public void discardCommentListener(ValueChangeEvent event) throws Exception
	{
		discardComment = event.getNewValue().toString();
	}

	public List<SearchCriterion> getScList() {
		return scList;
	}

	public void setScList(List<SearchCriterion> scList) {
		this.scList = scList;
	}

	public boolean isSimpleSearch() {
		return isSimpleSearch;
	}

	public void setSimpleSearch(boolean isSimpleSearch) {
		this.isSimpleSearch = isSimpleSearch;
	}
}
