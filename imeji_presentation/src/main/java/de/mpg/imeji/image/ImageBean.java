/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.imeji.image;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;

import org.apache.log4j.Logger;

import thewebsemantic.NotFoundException;
import de.mpg.imeji.album.AlbumBean;
import de.mpg.imeji.album.AlbumImagesBean;
import de.mpg.imeji.beans.Navigation;
import de.mpg.imeji.beans.SessionBean;
import de.mpg.imeji.lang.MetadataLabels;
import de.mpg.imeji.metadata.SingleEditBean;
import de.mpg.imeji.metadata.extractors.BasicExtractor;
import de.mpg.imeji.metadata.util.MetadataHelper;
import de.mpg.imeji.util.BeanHelper;
import de.mpg.imeji.util.ObjectLoader;
import de.mpg.jena.concurrency.locks.Locks;
import de.mpg.jena.controller.AlbumController;
import de.mpg.jena.controller.ImageController;
import de.mpg.jena.security.Operations.OperationsType;
import de.mpg.jena.security.Security;
import de.mpg.jena.util.ObjectHelper;
import de.mpg.jena.vo.CollectionImeji;
import de.mpg.jena.vo.Image;
import de.mpg.jena.vo.ImageMetadata;
import de.mpg.jena.vo.MetadataProfile;
import de.mpg.jena.vo.Properties.Status;
import de.mpg.jena.vo.Statement;

public class ImageBean implements Serializable
{
	public enum TabType
	{
		VIEW, EDIT, TECHMD;
	}

	private static Logger logger = Logger.getLogger(ImageBean.class);

	private String tab = null;
	private SessionBean sessionBean = null;
	private Image image;
	private String id = null;
	private boolean selected;
	private CollectionImeji collection;
	private List<String> techMd;
	private Navigation navigation;
	private MetadataProfile profile;
	private SingleEditBean edit;
	protected String prettyLink;
	private MetadataLabels labels;
	private SingleImageBrowse browse = null;

	public ImageBean(Image img) throws Exception
	{
		image = img;
		sessionBean = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);
		navigation = (Navigation)BeanHelper.getApplicationBean(Navigation.class);
		prettyLink = "pretty:editImage";
		labels = (MetadataLabels) BeanHelper.getSessionBean(MetadataLabels.class);
		if (sessionBean.getSelected().contains(image.getId()))
		{
			setSelected(true);
		}
		loadProfile();
		removeDeadMetadata();
		sortMetadataAccordingtoProfile();
	}
	

	public ImageBean() throws Exception
	{
		image = new Image();
		sessionBean = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);
		navigation = (Navigation)BeanHelper.getApplicationBean(Navigation.class);
		prettyLink = "pretty:editImage";
		labels = (MetadataLabels) BeanHelper.getSessionBean(MetadataLabels.class);
	}

	public String getInitPopup() throws Exception
	{
		labels.init(profile);
		return "";
	} 

	public void init() throws Exception
	{
		loadImage();
		loadCollection();
		loadProfile();
		removeDeadMetadata();
		sortMetadataAccordingtoProfile();
		initBrowsing();

		if (sessionBean.getSelected().contains(image.getId()))
		{
			setSelected(true);
		}

		labels.init(profile);

		edit = new SingleEditBean(image, profile, getPageUrl());

		cleanImageMetadata();
	}

	public void initBrowsing()
	{
		browse = new SingleImageBrowse((ImagesBean) BeanHelper.getSessionBean(ImagesBean.class), image);
	}

	private void sortMetadataAccordingtoProfile()
	{
		Collection<ImageMetadata> mdSorted = new ArrayList<ImageMetadata>();
		if (profile != null)
		{
			for (Statement st : profile.getStatements())
			{
				for (ImageMetadata md : image.getMetadataSet().getMetadata())
				{
					if (st.getName().equals(md.getNamespace()))
					{
						mdSorted.add(md);
					}
				}
			}
		}
		image.getMetadataSet().setMetadata(mdSorted);
	}

	public void loadImage()
	{
		try 
		{
			image = ObjectLoader.loadImage(ObjectHelper.getURI(Image.class, id), sessionBean.getUser());
		} 
		catch (NotFoundException e) 
		{
			image = new Image();
			BeanHelper.error(sessionBean.getLabel("image") + " " + id + sessionBean.getLabel("not_found"));
		}
		catch (Exception e) 
		{
			BeanHelper.error(sessionBean.getMessage("error_image_load"));
			logger.error(sessionBean.getMessage("error_image_load"), e);
		}
	}

	public void loadCollection()
	{
		try 
		{
			collection = ObjectLoader.loadCollection(getImage().getCollection(), sessionBean.getUser());
		} 
		catch (Exception e) 
		{
			BeanHelper.error(e.getMessage());
			e.printStackTrace();
			collection = null;
		}
	}

	public void loadProfile()
	{
		try 
		{
			profile = sessionBean.getProfileCached().get(image.getMetadataSet().getProfile());
			if (profile == null)
			{
				profile = ObjectLoader.loadProfile(image.getMetadataSet().getProfile(), sessionBean.getUser());
			}
			if (profile == null) 
			{
				profile = new MetadataProfile();
			}
		} 
		catch (Exception e) 
		{
			BeanHelper.error(sessionBean.getMessage("error_profile_load") + " " + image.getMetadataSet().getProfile() + "  " + sessionBean.getLabel("of") + " " + image.getId());
			profile = new MetadataProfile();
			logger.error("Error load profile " + image.getMetadataSet().getProfile() + " of image " + image.getId(), e);
		}
	}


	/**
	 * If a metadata is deleted in profile, or the type is changed, the metadata should be removed in image
	 * @throws Exception 
	 */
	public void removeDeadMetadata() throws Exception
	{
		boolean update = false;
		Collection<ImageMetadata> mds = new ArrayList<ImageMetadata>();
		try
		{
			for (ImageMetadata md : image.getMetadataSet().getMetadata())
			{
				boolean isStatement = false;

				for (Statement st : profile.getStatements())
				{
					if (st.getName().equals(md.getNamespace()))
					{
						isStatement = true;
						if(!st.getType().equals(md.getType().getURI()))
						{
							isStatement = false;
						}
					}
				}

				if (isStatement) mds.add(md);
				else update = true;
			}

			if (update)
			{
				ImageController imageController = new ImageController(sessionBean.getUser());
				image.getMetadataSet().setMetadata(mds);
				List<Image> l = new ArrayList<Image>();
				l.add(image);        		
				imageController.update(l);
			}
		}
		catch (Exception e) 
		{
			/* this user has not the priviliges to update the image */
		}

	}

	/**
	 * Remove empty metadata
	 */
	private void cleanImageMetadata()
	{
		for (int i=0; i < image.getMetadataSet().getMetadata().size(); i++)
		{
			if (MetadataHelper.isEmpty(((List<ImageMetadata>)image.getMetadataSet().getMetadata()).get(i)))
			{
				((List<ImageMetadata>)image.getMetadataSet().getMetadata()).remove(i);i--;
			}
		}
	}

	public String getInitLabels() throws Exception
	{
		labels.init(profile);
		return "";
	}

	public void initView() throws Exception
	{
		if (image == null || image.getId() == null || !image.getId().toString().equals(ObjectHelper.getURI(Image.class, id).toString())) 
		{
			init();
		}

		setTab(TabType.VIEW.toString());
	}

	public void initTechMd() throws Exception
	{
		if (image == null || image.getId() == null || !image.getId().toString().equals(ObjectHelper.getURI(Image.class, id).toString())) 
		{
			this.init();
		}
		setTab(TabType.TECHMD.toString());
	}

	public List<String> getTechMd() throws Exception
	{
		techMd = BasicExtractor.extractTechMd(image);
		return techMd;
	}

	public void setTechMd(List<String> md)
	{
		this.techMd = md;
	}

	public String getPageUrl()
	{
		return navigation.getApplicationUrl() + "image/" + this.id;
	}

	public String clearAll()
	{
		sessionBean.getSelected().clear();
		return "pretty:";
	}

	public CollectionImeji getCollection()
	{
		return collection;
	}

	public void setCollection(CollectionImeji collection)
	{
		this.collection = collection;
	}

	public void setImage(Image image)
	{
		this.image = image;
	}

	public Image getImage()
	{
		return image;
	}

	/**
	 * @param selected the selected to set
	 */
	public void setSelected(boolean selected)
	{
		this.selected = selected;
	}

	public boolean getSelected()
	{
		sessionBean = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);
		if (image != null && sessionBean.getSelected().contains(image.getId())) selected = true;
		else selected = false;
		return selected;
	}

	public String getThumbnailImageUrlAsString()
	{
		if (image.getThumbnailImageUrl() == null) return "/no_thumb";
		return image.getThumbnailImageUrl().toString();
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}

	public String getTab()
	{
		return tab;
	}

	public void setTab(String tab)
	{
		this.tab = tab.toUpperCase();
	}

	public String getNavigationString()
	{
		return "pretty:viewImage";
	}

	public SessionBean getSessionBean()
	{
		return sessionBean;
	}

	public void setSessionBean(SessionBean sessionBean)
	{
		this.sessionBean = sessionBean;
	}

	public String addToActiveAlbum() throws Exception
	{
		AlbumBean activeAlbum = sessionBean.getActiveAlbum();
		AlbumController ac = new AlbumController(sessionBean.getUser());
		if (activeAlbum.getAlbum().getImages().contains(image.getId()))
		{
			BeanHelper.error(((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getLabel("image") + " " + image.getFilename() + " " + ((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getMessage("already_in_active_album"));       
		}
		else
		{
			activeAlbum.getAlbum().getImages().add(image.getId());
			ac.update(activeAlbum.getAlbum());
			BeanHelper.info(((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getLabel("image") + " " + image.getFilename() + " " + ((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getMessage("added_to_active_album"));       
		}
		return "";
	}

	public String removeFromAlbum() throws Exception
	{
		AlbumImagesBean aib = (AlbumImagesBean) BeanHelper.getSessionBean(AlbumImagesBean.class);    	
		AlbumController ac = new AlbumController(sessionBean.getUser());
		aib.getAlbum().getAlbum().getImages().remove(image.getId());
		ac.update(aib.getAlbum().getAlbum());
		if(getIsInActiveAlbum()) sessionBean.getActiveAlbum().getAlbum().getImages().remove(image.getId());
		BeanHelper.info(sessionBean.getLabel("image") + " " + image.getFilename() + " " + sessionBean.getMessage("success_album_remove_from"));
		return "pretty:";
	}

	public boolean getIsInActiveAlbum()
	{
		if (sessionBean.getActiveAlbum() != null)
		{
			return sessionBean.getActiveAlbum().getAlbum().getImages().contains(image.getId());
		}
		return false;
	}

	public void selectedChanged(ValueChangeEvent event)
	{
		sessionBean = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);

		if (event.getNewValue().toString().equals("true") && !sessionBean.getSelected().contains(image.getId()))
		{
			setSelected(true);
			select();
		}
		else if (event.getNewValue().toString().equals("false") && sessionBean.getSelected().contains(image.getId()))
		{
			setSelected(false);
			select();
		}
	}

	public String select()
	{
		if (!selected)
		{
			((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getSelected().remove(image.getId());
		}
		else
		{
			((SessionBean)BeanHelper.getSessionBean(SessionBean.class)).getSelected().add(image.getId());
		}
		return "";
	}

	public MetadataProfile getProfile() {
		return profile;
	}

	public void setProfile(MetadataProfile profile) {
		this.profile = profile;
	}

	public List<SelectItem> getStatementMenu()
	{
		List<SelectItem> statementMenu = new ArrayList<SelectItem>();
		if (profile == null)
		{
			loadProfile();
		}
		for (Statement s : profile.getStatements())
		{
			statementMenu.add(new SelectItem(s.getName(), s.getLabels().iterator().next().toString()));
		}
		return statementMenu;
	}

	public SingleEditBean getEdit() 
	{
		return edit;
	}

	public void setEdit(SingleEditBean edit) 
	{
		this.edit = edit;
	}

	public boolean isLocked()
	{
		return Locks.isLocked(this.image.getId().toString(), sessionBean.getUser().getEmail());
	}

	public boolean isEditable() 
	{
		Security security = new Security();
		return security.check(OperationsType.UPDATE, sessionBean.getUser(), image) && image != null &&  !image.getProperties().getStatus().equals(Status.WITHDRAWN);
	}

	public boolean isVisible() 
	{
		Security security = new Security();
		return security.check(OperationsType.READ, sessionBean.getUser(), image);
	}

	public boolean isDeletable() 
	{
		Security security = new Security();
		return security.check(OperationsType.DELETE, sessionBean.getUser(), image);
	}

	public SingleImageBrowse getBrowse() {
		return browse;
	}

	public void setBrowse(SingleImageBrowse browse) {
		this.browse = browse;
	}

	public String getDescription() 
	{
		for (Statement s : getProfile().getStatements())
		{
			if (s.isDescription())
			{
				for (ImageMetadata md : this.getImage().getMetadataSet().getMetadata())
				{
					if (md.getNamespace().equals(s.getName()))
					{
						return md.getSearchValue();
					}
				}
			}
		}
		return image.getFilename();
	}


}
