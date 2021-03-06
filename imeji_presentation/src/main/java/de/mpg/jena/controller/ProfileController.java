/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.jena.controller;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import thewebsemantic.NotFoundException;
import thewebsemantic.RDF2Bean;

import de.mpg.jena.ImejiBean2RDF;
import de.mpg.jena.ImejiJena;
import de.mpg.jena.ImejiRDF2Bean;
import de.mpg.jena.search.ImejiSPARQL;
import de.mpg.jena.search.Search;
import de.mpg.jena.search.SearchResult;
import de.mpg.jena.util.ObjectHelper;
import de.mpg.jena.vo.Grant;
import de.mpg.jena.vo.Grant.GrantType;
import de.mpg.jena.vo.MetadataProfile;
import de.mpg.jena.vo.Properties.Status;
import de.mpg.jena.vo.Statement;
import de.mpg.jena.vo.User;

public class ProfileController extends ImejiController
{
	private static ImejiRDF2Bean imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.profileModel);
	private static ImejiBean2RDF imejiBean2RDF = new ImejiBean2RDF(ImejiJena.profileModel);
	
    public ProfileController(User user)
    {
        super(user);
    }
    
    /**
     * Creates a new collection. 
     * - Add a unique id
     * - Write user properties
     * @param ic
     * @param user
     */
    public URI create(MetadataProfile mdp) throws Exception
    {
     	imejiBean2RDF = new ImejiBean2RDF(ImejiJena.profileModel);
    	writeCreateProperties(mdp.getProperties(), user);
        mdp.getProperties().setStatus(Status.PENDING);
        if (mdp.getId() == null)
        {
	        URI uri = ObjectHelper.getURI(MetadataProfile.class, Integer.toString(getUniqueId()));
	        mdp.setId(uri);
        }
        imejiBean2RDF.create(imejiBean2RDF.toList(mdp), user);
        addCreatorGrant(mdp, user);
        return mdp.getId();
    }
    
	private User addCreatorGrant(MetadataProfile p, User user) throws Exception
	{
		GrantController gc = new GrantController(user);
		Grant grant = new Grant(GrantType.PROFILE_ADMIN, p.getId());
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
    public void update(MetadataProfile mdp) throws Exception
    {
    	imejiBean2RDF = new ImejiBean2RDF(ImejiJena.profileModel);
    	writeUpdateProperties(mdp.getProperties(), user);
        imejiBean2RDF.saveDeep(imejiBean2RDF.toList(mdp), user);
    }
    
    public void release(MetadataProfile mdp) throws Exception
    {
    	mdp.getProperties().setStatus(Status.RELEASED);
    	mdp.getProperties().setVersionDate(new Date());
    	update(mdp);
    }
    
    public void delete(MetadataProfile mdp, User user) throws Exception
    {
    	imejiBean2RDF = new ImejiBean2RDF(ImejiJena.profileModel);
    	imejiBean2RDF.delete(imejiBean2RDF.toList(mdp), user);
    	GrantController gc = new GrantController(user);
		gc.removeAllGrantsFor(user, mdp.getId());
    }
    
    public void withdraw(MetadataProfile mdp, User user) throws Exception
    {
    	mdp.getProperties().setStatus(Status.WITHDRAWN);
    	mdp.getProperties().setVersionDate(new Date());
    	update(mdp);
    }
    
    public int countAllProfiles()
    {
		return ImejiSPARQL.execCount("SELECT ?s count(DISTINCT ?s) WHERE { ?s a <http://imeji.mpdl.mpg.de/profile>}");
    }
    
    /**
     * @deprecated
     * @return
     */
    public List<MetadataProfile> retrieveAll()
    {
    	rdf2Bean = new RDF2Bean(ImejiJena.profileModel);
    	return (List<MetadataProfile>)rdf2Bean.load(MetadataProfile.class);
    }
    
    /**
     * To be replaced, with a more generic method
     * 
     * @return
     * @deprecated
     */
    public  List<MetadataProfile> search()
    {
    	String q = "SELECT DISTINCT ?s WHERE {?s a <http://imeji.mpdl.mpg.de/mdprofile> . ?s <http://imeji.mpdl.mpg.de/properties> ?props . ?props <http://imeji.mpdl.mpg.de/status> ?status " +
    			".FILTER( ";
    	
    	q += "?status=<http://imeji.mpdl.mpg.de/status/RELEASED> || (?status!=<http://imeji.mpdl.mpg.de/status/WITHDRAWN> ";
    	
    	int i=0;
    	
    	if (user != null && user.getGrants().size() >0)
    	{
    		q += "&& (";
			for(Grant g : user.getGrants())
	    	{
	    		if (GrantType.SYSADMIN.equals(g.getGrantType()))
	    		{
	    			if (i > 0) q+= " || ";
	    			q += " true ";
	    			i++;
	    		}
	    		else if (GrantType.PROFILE_EDITOR.equals(g.getGrantType())|| GrantType.PROFILE_ADMIN.equals(g.getGrantType()))
	    		{
	    			if (i > 0) q+= " || ";
	    			q += " ?s=<" + g.getGrantFor() +"> ";
	    			i++;
	    		}
	    	}
			q += ")";
    	}
    	q += " ))}";
    	
    	return ImejiSPARQL.execAndLoad(q, MetadataProfile.class);
    }
    
    
    public MetadataProfile retrieve(String id) throws Exception
    {
    	imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.profileModel);
    	return retrieve(ObjectHelper.getURI(MetadataProfile.class, id));
    }
    
    public MetadataProfile retrieve(URI uri) throws Exception
    {
    	imejiRDF2Bean = new ImejiRDF2Bean(ImejiJena.profileModel);
    	MetadataProfile p =  ((MetadataProfile)imejiRDF2Bean.load(uri.toString(), user));
    	Collections.sort((List<Statement>) p.getStatements());
    	return p;
    }
    
    @Override
    protected String getSpecificFilter() throws Exception
    {
       return "";
    }

    @Override
    protected String getSpecificQuery() throws Exception
    {
        return "";
    }
}
