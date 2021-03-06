/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.jena.controller;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import thewebsemantic.NotBoundException;
import de.escidoc.core.client.Authentication;
import de.escidoc.core.client.ItemHandlerClient;
import de.mpg.imeji.util.LoginHelper;
import de.mpg.imeji.util.ObjectLoader;
import de.mpg.imeji.util.PropertyReader;
import de.mpg.jena.ImejiBean2RDF;
import de.mpg.jena.ImejiJena;
import de.mpg.jena.ImejiRDF2Bean;
import de.mpg.jena.search.ImejiSPARQL;
import de.mpg.jena.search.Search;
import de.mpg.jena.search.SearchResult;
import de.mpg.jena.util.MetadataFactory;
import de.mpg.jena.util.ObjectHelper;
import de.mpg.jena.vo.CollectionImeji;
import de.mpg.jena.vo.Grant;
import de.mpg.jena.vo.Grant.GrantType;
import de.mpg.jena.vo.Image;
import de.mpg.jena.vo.Image.Visibility;
import de.mpg.jena.vo.ImageMetadata;
import de.mpg.jena.vo.Properties.Status;
import de.mpg.jena.vo.User;

public class ImageController extends ImejiController
{
    private String additionalQuery = "";
    private static Logger logger = null;

    private static ImejiRDF2Bean imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel);
	private static ImejiBean2RDF imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);

    public ImageController(User user)
    {
        super(user);
        logger = Logger.getLogger(ImageController.class);
    }
   
    public void create(Image img, URI coll) throws Exception
    {
        CollectionController cc = new CollectionController(user);
        CollectionImeji ic = cc.retrieve(coll);
    	writeCreateProperties(img.getProperties(), user);
    	if (Status.PENDING.equals(ic.getProperties().getStatus())) img.setVisibility(Visibility.PRIVATE);
    	else img.setVisibility(Visibility.PUBLIC);
        img.setCollection(coll);
        img.setId(ObjectHelper.getURI(Image.class, Integer.toString(getUniqueId())));
        img.getMetadataSet().setProfile(ic.getProfile());
        imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
        imejiBean2RDF.create(imejiBean2RDF.toList(img), user);
        ic.getImages().add(img.getId());
        cc.update(ic);
    }

    public void create(Collection<Image> images, URI coll) throws Exception
    {
    	CollectionController cc = new CollectionController(user);
    	CollectionImeji ic = cc.retrieve(coll);
        imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
        
        for (Image img : images)
        {
        	 writeCreateProperties(img.getProperties(), user);
        	 if (Status.PENDING.equals(ic.getProperties().getStatus())) img.setVisibility(Visibility.PRIVATE);
        	 else img.setVisibility(Visibility.PUBLIC);
             img.setCollection(coll);
             img.setId(ObjectHelper.getURI(Image.class, Integer.toString(getUniqueId())));
             img.getMetadataSet().setProfile(ic.getProfile());
             imejiBean2RDF.create(imejiBean2RDF.toList(img), user);
             ic.getImages().add(img.getId());
        }
        cc.update(ic);
    }
    

    public void update(Image img) throws Exception
    {
    	Collection<Image> im = new ArrayList<Image>();
    	im.add(img);
    	update(im);
    }

    public void update(Collection<Image> images) throws Exception
    {
    	imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
    	List<Object> imBeans = new ArrayList<Object>();
    	for (Image img : images)
        {
    		for(int i=0; i< img.getMetadataSet().getMetadata().size(); i++)
    		{
    			((List<ImageMetadata>)img.getMetadataSet().getMetadata()).set(i, MetadataFactory.copyMetadata(((List<ImageMetadata>)img.getMetadataSet().getMetadata()).get(i)));
    		}
    		imBeans.add(img);
        }
    	
    	imejiBean2RDF.saveDeep(imBeans, user);
    }
    
    /**
     * User ObjectLoader to load image
	 *
     * @param imgUri
     * @return
     * @throws Exception
     */
    public Image retrieve(URI imgUri) throws Exception
    {
    	imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel); 
    	return (Image)imejiRDF2Bean.load(imgUri.toString(), user);
    }

    /**
     * User ObjectLoader instead
     * @deprecated
     * @param id
     * @return
     * @throws Exception
     */
    public Image retrieve(String id) throws Exception
    {
    	imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel);
    	return (Image)imejiRDF2Bean.load(ObjectHelper.getURI(Image.class, id).toString(), user);
    }
    
    public Collection<Image> retrieveAll()
    {
    	imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.imageModel);
    	return imejiRDF2Bean.load(Image.class);
    }
    
    /**
     * NOT WORKING
     * @param uri
     */
    public void getGraph(URI uri)
    {
    	additionalQuery = " . <" + uri.toString() + "> <http://imeji.mpdl.mpg.de/image/metadata> ?md . ?md <http://www.w3.org/2000/01/rdf-schema#member> ?list . ?list <http://purl.org/dc/terms/type> ?type";
//    	QuerySPARQL querySPARQL = new QuerySPARQLImpl();
//        String query = querySPARQL.createConstructQuery(new ArrayList<SearchCriterion>(), null,	"http://imeji.mpdl.mpg.de/image", additionalQuery , "?s=<http://imeji.mpdl.mpg.de/image/111>", 1, 0, user, false);
//    	ImejiSPARQL.execConstruct(query).write(System.out, "RDF/XML-ABBREV");
    }
    
    /**
     * Get the number of all images
     * @return
     */
    public int allImagesSize()
    {
    	return ImejiSPARQL.execCount("SELECT count(DISTINCT ?s) WHERE { ?s a <http://imeji.mpdl.mpg.de/image>} ");
    }
    
    public SearchResult searchImages(List<SearchCriterion> scList, SortCriterion sortCri)
    {
    	Search search = new Search("http://imeji.mpdl.mpg.de/image", null);
    	return search.search(scList, sortCri, simplifyUser(null));
    }
    
    public SearchResult searchImagesInContainer(URI containerUri, List<SearchCriterion> scList, SortCriterion sortCri, int limit, int offset)
    {
    	Search search = new Search("http://imeji.mpdl.mpg.de/image", containerUri.toString());
    	return search.search(scList, sortCri, simplifyUser(containerUri));
    }
    
    public int countImages(List<SearchCriterion> scList)
    {
    	Search search = new Search("http://imeji.mpdl.mpg.de/image",null);
//    	List<String> uris = search.searchAdvanced(scList, null, simplifyUser(null));
//    	return uris.size();
    	return search.search(scList, null, simplifyUser(null)).getNumberOfRecords();
    }
    
    public int countImagesInContainer(URI containerUri, List<SearchCriterion> scList)
    {
    	Search search = new Search("http://imeji.mpdl.mpg.de/image", containerUri.toString());
    	List<String> uris = search.searchAdvanced(scList, null, simplifyUser(containerUri));
    	return uris.size();
    }
    
    public Collection<Image> loadImages(List<String> uris, int limit, int offset)
    {
    	LinkedList<Image> images = new LinkedList<Image>();
    	ImejiRDF2Bean reader = new ImejiRDF2Bean(ImejiJena.imageModel);
    	
    	int counter = 0;
        for (String s : uris)
        {
        	if (offset <= counter && (counter < (limit + offset) || limit == -1)) 
        	{
        		try 
        		{
        			Image image = (Image) reader.load(s, user);
        			
        			if (image != null)
        			{
        				if( image.getMetadataSet().getProfile() == null) 
            			{
        					logger.error("Error by loading image "  + s + " : No related profile found");
            				image.getMetadataSet().setProfile(ObjectLoader.loadCollection(image.getCollection(), user).getProfile());        				
            				update(image);
            				logger.info("Profile added: "  + image.getMetadataSet().getProfile() );
            			}
        				images.add(image);
        			}
				} 
        		catch (Exception e) 
				{
					logger.error("Error loading image " + s + ":", e);
				}
        	}
         	counter ++;
        }
		return images;
    }
    
    /**
     * Increase performance by restricting grants to the only grants needed
     * @param user
     * @return
     */
    public User simplifyUser(URI containerUri)
    {
    	if (user == null)
    	{
    		return null;
    	}

    	User simplifiedUser = new User();

    	for (Grant g :user.getGrants()) 
    	{
    		if (GrantType.SYSADMIN.equals(g.getGrantType()))
			{
				simplifiedUser.getGrants().add(g);
			}
    		else if (containerUri != null && containerUri.toString().contains("collection") && containerUri.toString().equals(g.getGrantFor().toString()))
			{
				simplifiedUser.getGrants().add(g);
			}
			else if (containerUri != null && containerUri.toString().contains("album") && g.getGrantFor().toString().contains("collection"))
			{
				simplifiedUser.getGrants().add(g);
			}
			else if (containerUri == null &&  g.getGrantFor() != null && g.getGrantFor().toString().contains("collection"))
			{
				simplifiedUser.getGrants().add(g);
			}
			else
			{
				//simplifiedUser.getGrants().add(g);
			}
		}
    	return simplifiedUser;
    }

    public void delete(Image img, User user) throws Exception
    {
    	if (img != null)
    	{
	    	imejiBean2RDF = new ImejiBean2RDF(ImejiJena.imageModel);
			try 
			{
				imejiBean2RDF.delete(imejiBean2RDF.toList(img), user);
			}
			catch (NotBoundException e) 
			{
				logger.warn(img.getId().toString() + " not bound! Deleting...");
				deleteObjects(img.getId().toString());
				logger.warn("... done.");
			}
			removeImageFromEscidoc(img.getEscidocId());
    	}
    }

    public void release(Image img) throws Exception
    {
    	if (Status.PENDING.equals(img.getProperties().getStatus()))
    	{
    		img.getProperties().setStatus(Status.RELEASED);
        	img.setVisibility(Visibility.PUBLIC);
        	update(img);
    	}
    }
    
    
    public void withdraw(Image img) throws Exception
    {
    	if (img.getProperties().getStatus().equals(Status.RELEASED))
    	{
    		img.getProperties().setStatus(Status.WITHDRAWN);
    		img.setVisibility(Visibility.PUBLIC);
    		update(img);
    		if (img.getEscidocId() != null)
        	{
        		removeImageFromEscidoc(img.getEscidocId());
            	img.setEscidocId(null);
        	}
    	}
    	else throw new RuntimeException("Only released images can be discarded: " + img.getId() + " has status " + img.getProperties().getStatus());
    }
    
    public void removeImageFromEscidoc(String id)
    {
    	try 
    	{
    		String username = PropertyReader.getProperty("imeji.escidoc.user");
    	    String password = PropertyReader.getProperty("imeji.escidoc.password");
    		Authentication auth = new Authentication(new URL(PropertyReader.getProperty("escidoc.framework_access.framework.url")), username, password);
    		ItemHandlerClient handler = new ItemHandlerClient(auth.getServiceAddress());
    		handler.setHandle(auth.getHandle());
    		handler.delete(id);
		} 
    	catch (Exception e) 
    	{
    		logger.error("Error removing image from eSciDoc (" + id + ")", e);
			throw new RuntimeException("Error removing image from eSciDoc (" + id + ")", e);
		}
    }

    public String getEscidocUserHandle() throws Exception
    {
        String userName = PropertyReader.getProperty("imeji.escidoc.user");
        String password = PropertyReader.getProperty("imeji.escidoc.password");
        return LoginHelper.login(userName, password);
    }


    @Override
    @Deprecated
    protected String getSpecificFilter() throws Exception
    {
        // Add filters for user management
        String filter = "(";
        if (user == null)
        {
            filter += "?collStatus = <http://imeji.mpdl.mpg.de/status/RELEASED> && ?visibility = <http://imeji.mpdl.mpg.de/image/visibility/PUBLIC>";
        }
        else
        {
            String userUri = "http://xmlns.com/foaf/0.1/Person/" + URLEncoder.encode(user.getEmail(), "UTF-8");
            filter += "(?collStatus = <http://imeji.mpdl.mpg.de/status/RELEASED> && ?visibility = <http://imeji.mpdl.mpg.de/image/visibility/PUBLIC>)";
            filter += " || ?collCreatedBy=<" + userUri + ">";
            for (Grant grant : user.getGrants())
            {
                switch (grant.getGrantType())
                {
                    case CONTAINER_ADMIN: // Add specifics here
                    default:
                    	if (grant.getGrantFor() != null) filter += " || ?collection=<" + grant.getGrantFor().toString() + ">";
                }
            }
        }
        filter += ")";
        return filter;
    }

    @Override
    protected String getSpecificQuery() throws Exception
    {
        return additionalQuery
                + " . ?s <http://imeji.mpdl.mpg.de/collection> ?collection . ?s <http://imeji.mpdl.mpg.de/visibility> ?visibility . ?collection <http://imeji.mpdl.mpg.de/properties> ?collprops . ?collprops <http://imeji.mpdl.mpg.de/createdBy> ?collCreatedBy . ?collprops <http://imeji.mpdl.mpg.de/status> ?collStatus ";
    }
}
