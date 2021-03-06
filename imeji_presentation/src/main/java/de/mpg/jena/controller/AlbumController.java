/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.jena.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import thewebsemantic.NotFoundException;
import de.mpg.jena.ImejiBean2RDF;
import de.mpg.jena.ImejiJena;
import de.mpg.jena.ImejiRDF2Bean;
import de.mpg.jena.search.Search;
import de.mpg.jena.search.SearchResult;
import de.mpg.jena.security.Security;
import de.mpg.jena.util.ObjectHelper;
import de.mpg.jena.vo.Album;
import de.mpg.jena.vo.Grant;
import de.mpg.jena.vo.Grant.GrantType;
import de.mpg.jena.vo.Image;
import de.mpg.jena.vo.Properties.Status;
import de.mpg.jena.vo.User;


public class AlbumController extends ImejiController
{
	private static ImejiRDF2Bean imejiRDF2Bean = null;
	private static ImejiBean2RDF imejiBean2RDF = null;
	
	private static Logger logger = Logger.getLogger(CollectionController.class);
	
	public AlbumController(User user)
	{
		super(user);
		imejiBean2RDF = new ImejiBean2RDF(ImejiJena.albumModel);
		imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.albumModel);
	}
	
	/**
	 * Creates a new collection. 
	 * - Add a unique id
	 * - Write user properties
	 * @param ic
	 * @param user
	 */
	public void create(Album ic) throws Exception
	{
		writeCreateProperties(ic.getProperties(), user);
	    ic.getProperties().setStatus(Status.PENDING); 
		ic.setId(new URI("http://imeji.mpdl.mpg.de/album/" + getUniqueId()));
		imejiBean2RDF = new ImejiBean2RDF(ImejiJena.albumModel);
		imejiBean2RDF.create(imejiBean2RDF.toList(ic), user);
		imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.albumModel);
		//ic = (Album) imejiRDF2Bean.load(ic.getId().toString(), user);
		user = addCreatorGrant(ic, user);
		cleanGraph(ImejiJena.albumModel);
	}
	
	public User addCreatorGrant(Album alb, User user) throws Exception
	{
	 	GrantController gc = new GrantController(user);
		Grant grant = new Grant(GrantType.CONTAINER_ADMIN, alb.getId());
		gc.addGrant(user, grant);
		UserController uc = new UserController(user);
		return uc.retrieve(user.getEmail());
	}
	
	/**
	 * Updates a collection
	 * -Logged in users:
	 * --User is collection owner
	 * --OR user is collection editor
	 * @param ic
	 * @param user
	 * @throws Exception 
	 */
	public void update(Album ic) throws Exception
	{
		imejiBean2RDF = new ImejiBean2RDF(ImejiJena.albumModel);
		writeUpdateProperties(ic.getProperties(), user);
		imejiBean2RDF.saveDeep(imejiBean2RDF.toList(ic), user);
		cleanGraph(ImejiJena.albumModel);
	}
	
	/**
     * Updates a collection
     * -Logged in users:
     * --User is collection owner
     * --OR user is collection editor
     * @param ic
     * @param user
	 * @throws Exception 
     */
    public Album retrieve(String id) throws Exception
    {
    	imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.albumModel);
    	return (Album) imejiRDF2Bean.load(ObjectHelper.getURI(Album.class, id).toString(), user);
    }

    public Album retrieve(URI selectedAlbumId) throws Exception
    {
    	imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.albumModel);
        return (Album) imejiRDF2Bean.load(selectedAlbumId.toString(), user);
    }
	
    @Deprecated
	public Collection<Album> retrieveAll()
	{
		imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.albumModel);
		Security security = new Security();
		if (security.isSysAdmin(user))
		{
			return imejiRDF2Bean.load(Album.class);
		}
		return new ArrayList<Album>();
	}
	
	public void delete(Album album, User user) throws Exception
	{
		imejiBean2RDF = new ImejiBean2RDF(ImejiJena.albumModel);
		imejiBean2RDF.delete(imejiBean2RDF.toList(album), user);
		GrantController gc = new GrantController(user);
		gc.removeAllGrantsFor(user, album.getId());
		cleanGraph(ImejiJena.albumModel);
	}
	
	public void release(Album album) throws Exception
    {
		if (hasImageLocked(album.getImages(), user))
		{
			throw new RuntimeException("Album has at least one image locked by an other user.");
		}
		else if(hasPendingImage(album.getImages()))
		{
			throw new RuntimeException("Album has at least one image with status pending. All images have to be released to release an album");
		}
		else
		{	
			album.getProperties().setStatus(Status.RELEASED);
			album.getProperties().setVersionDate(new Date());
			
			ImageController imageController = new ImageController(user);
			
			for(URI uri: album.getImages())
		    {
		    	try 
		    	{
		    		imageController.release(imageController.retrieve(uri));
				} 
		    	catch (NotFoundException e) 
				{
					logger.error("Release image error: " + uri + " could not be found");
				}
		    	catch (Exception e) 
		    	{
					logger.error("You are not allowed to release image " + uri + ". I could be deleted by it's owner." );
				}
		    }
	        update(album);
		}
    }
	
	public boolean hasPendingImage(Collection<URI> images) throws Exception
	{
		ImageController c = new ImageController(user);
		for (URI im : images)
		{
			Image vo = c.retrieve(im);
			if (Status.PENDING.equals(vo.getProperties().getStatus()))
			{
				return true;
			}
		}
		return false;
	}
	
	
	public void withdraw(Album album) throws Exception
    {
		album.getProperties().setStatus(Status.WITHDRAWN);
		album.getProperties().setVersionDate(new Date());
		album.getImages().clear();
		update(album);
    }

	/**
	 * Search for albums
	 * - Logged-out user:
	 * --Collection must be released
	 * 
	 * -Logged-in users
	 * --Collection is released
	 * --OR Collection is pending AND user is owner
	 * --OR Collection is withdrawn AND user is owner
	 * --OR Collection is pending AND user has grant "Container Editor" for it.
	 * @param user
	 * @param scList
	 * @return
	 */
	public SearchResult search(List<SearchCriterion> scList, SortCriterion sortCri, int limit, int offset)
	{
	    Search search = new Search("http://imeji.mpdl.mpg.de/album", null);
	    return search.search(scList, sortCri, user);
	}
	
	public Collection<Album> load(List<String> uris, int limit, int offset)
    {
    	LinkedList<Album> albs = new LinkedList<Album>();
    	ImejiRDF2Bean reader = new ImejiRDF2Bean(ImejiJena.albumModel);
    	
    	int counter = 0;
        for (String s : uris)
        {
        	if (offset <= counter && (counter < (limit + offset) || limit == -1)) 
        	{
        		try 
        		{
        			albs.add((Album) reader.load(s, user));
				} 
        		catch (Exception e) 
				{
					logger.error("Error loading image " + s);
				}
        	}
         	counter ++;
        }
		return albs;
    }

	@Override
	@Deprecated
    protected String getSpecificQuery() throws Exception
    {
      return " . ?s <http://imeji.mpdl.mpg.de/properties> ?props . ?props <http://imeji.mpdl.mpg.de/createdBy> ?createdBy . ?props <http://imeji.mpdl.mpg.de/status> ?status";
    }
    
    @Override
    protected String getSpecificFilter() throws Exception
    {
        //Add filters for user management
        String filter ="(";
      
          
         
         if(user==null)
         {
             
             filter += "?status = <http://imeji.mpdl.mpg.de/status/RELEASED>";
         }
         else
         {
            
             String userUri = "http://xmlns.com/foaf/0.1/Person/" + URLEncoder.encode(user.getEmail(), "UTF-8");
             filter += "?status = <http://imeji.mpdl.mpg.de/status/RELEASED> || ?createdBy=<" +  userUri + ">";
             
            
                 for(Grant grant : user.getGrants())
                 {
                     switch(grant.getGrantType())
                     {
                         case CONTAINER_ADMIN : //Add specifics here
                             
                         default : filter += " || ?s=<" + grant.getGrantFor().toString() + ">";
                     }
                     
                 }
             }
         
     
          filter += ")";
         return filter;
    }

	
}
