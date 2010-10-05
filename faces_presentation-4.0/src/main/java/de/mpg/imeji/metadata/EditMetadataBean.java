package de.mpg.imeji.metadata;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.faces.model.SelectItem;
import de.mpg.imeji.beans.SessionBean;
import de.mpg.imeji.util.BeanHelper;
import de.mpg.imeji.util.ProfileHelper;
import de.mpg.jena.controller.ImageController;
import de.mpg.jena.util.ComplexTypeHelper;
import de.mpg.jena.vo.Image;
import de.mpg.jena.vo.ImageMetadata;
import de.mpg.jena.vo.MetadataProfile;
import de.mpg.jena.vo.Statement;

public class EditMetadataBean
{
    private List<Image> images;
    private Image image;
    private Map<URI, MetadataProfile> profiles;
    private SessionBean sb;
    // private List<MdField> mdFields;
    private List<SelectItem> statementMenu;
    private MetadataProfile profile;
    private List<MetadataBean> metadata;
    private int mdPosition;
    private String prettyLink;

    public EditMetadataBean(List<Image> images)
    {
        this.prettyLink = "pretty:selected";
        this.sb = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);
        this.images = images;
        this.image = null;
        profiles = ProfileHelper.loadProfiles(images);
        // mdFields = ProfileHelper.getFields(profiles);
        metadata = new ArrayList<MetadataBean>();
        if (!profiles.isEmpty())
        {
            profile = profiles.values().iterator().next();
            statementMenu = new ArrayList<SelectItem>();
            for (Statement s : profile.getStatements())
            {
                statementMenu.add(new SelectItem(s.getName(), s.getName()));
            }
            addMetadata();
        }
    }

    public EditMetadataBean(Image image)
    {
        this.prettyLink = "pretty:editImage";
        this.sb = (SessionBean)BeanHelper.getSessionBean(SessionBean.class);
        this.image = image;
        profile = ProfileHelper.loadProfiles(image);
        metadata = new ArrayList<MetadataBean>();
        statementMenu = new ArrayList<SelectItem>();
        for (Statement s : profile.getStatements())
            statementMenu.add(new SelectItem(s.getName(), s.getName()));
        if (image.getMetadata().size() != 0)
            addImageMetadataForEdit(image);
        else
            addMetadata();
    }

    public String expandAllMetadata()
    {
        metadata.clear();
        for (Statement s : profile.getStatements())
        {
            Map<String, String> mdAlreadyDisplayed = new HashMap<String, String>();
            if (image != null)
            {
                for (ImageMetadata im : image.getMetadata())
                {
                    if (im.getName() == s.getName())
                    {
                        MetadataBean mb = new MetadataBean(profile, s, im);
                        mb.setPrettyLink(prettyLink);
                        metadata.add(mb);
                        mdAlreadyDisplayed.put(s.getName(), s.getName());
                    }
                }
            }
            if (!mdAlreadyDisplayed.containsKey(s.getName()))
            {
                MetadataBean mb = new MetadataBean(profile, s);
                mb.setPrettyLink(prettyLink);
                metadata.add(mb);
            }
        }
        return prettyLink;
    }

    public String addImageMetadataForEdit(Image image)
    {
        for (Statement s : profile.getStatements())
        {
            for (ImageMetadata im : image.getMetadata())
            {
                if (im.getName() == s.getName())
                {
                    MetadataBean mb = new MetadataBean(profile, s, im);
                    mb.setPrettyLink(prettyLink);
                    metadata.add(mb);
                }
            }
        }
        return prettyLink;
    }

    public String save()
    {
        if (!edit())
        {
            BeanHelper.error("Error editing images");
        }
        BeanHelper.info("Images edited");
        return prettyLink;
    }

    public boolean edit()
    {
        try
        {
            ImageController ic = new ImageController(sb.getUser());
            if (images != null && images.size() > 0 && "pretty:selected".equals(prettyLink))
            {
                for (Image im : images)
                {
                    im = setImageMetadata(im, metadata);
                }
                ic.update(images);
            }
            else if ("pretty:editImage".equals(prettyLink) && image != null)
            {
                image = updateImageMetadata(image, metadata);
                ic.update(image);
            }
        }
        catch (Exception e)
        {
            return false;
        }
        return true;
    }

    public Image updateImageMetadata(Image im, List<MetadataBean> mdbs) throws Exception
    {
        im.getMetadata().clear();
        // add new mdvalues (overwrite old)
        for (MetadataBean mdb : metadata)
            im.getMetadata().add(mdb.getMetadata());
        return im;
    }

    public Image setImageMetadata(Image im, List<MetadataBean> mdbs) throws Exception
    {
        Map<String, List<ImageMetadata>> mdMap = new HashMap<String, List<ImageMetadata>>();
        List<ImageMetadata> newMetadata = new ArrayList<ImageMetadata>();
        for (ImageMetadata imd : im.getMetadata())
        {
            if (mdMap.containsKey(imd.getName()))
            {
                List<ImageMetadata> imList = mdMap.get(imd.getName());
                imList.add(imd);
            }
            else
            {
                List<ImageMetadata> imList = new ArrayList<ImageMetadata>();
                imList.add(imd);
                mdMap.put(imd.getName(), imList);
            }
        }
        im.getMetadata().clear();
        // add new mdvalues (overwrite old)
        for (MetadataBean mdb : metadata)
        {
            if (mdMap.containsKey(mdb.getMetadata().getName()))
            {
                List<ImageMetadata> imList = mdMap.get(mdb.getMetadata().getName());
                imList.clear();
            }
            im.getMetadata().add(mdb.getMetadata());
        }
        for (List<ImageMetadata> imList : mdMap.values())
        {
            im.getMetadata().addAll(imList);
        }
        return im;
    }

    public boolean hasMetadata(Image im, String name)
    {
        for (ImageMetadata md : im.getMetadata())
        {
            if (name.equals(md.getName()))
                return true;
        }
        return false;
    }

    public String addMetadata()
    {
        if (profile.getStatements() != null && profile.getStatements().size() > 0)
        {
            MetadataBean mb = new MetadataBean(profile, profile.getStatements().get(0));
            mb.setPrettyLink(prettyLink);
            if (metadata.size() == 0)
            {
                metadata.add(mb);
            }
            else
            {
                metadata.add(getMdPosition() + 1, mb);
            }
            System.err.println("prettyLink = " + prettyLink);
        }
        return prettyLink;
    }

    public String getPrettyLink()
    {
        return prettyLink;
    }

    public void setPrettyLink(String prettyLink)
    {
        this.prettyLink = prettyLink;
    }

    public String removeMetadata()
    {
        if (metadata.size() > 0)
        {
            metadata.remove(getMdPosition());
        }
        return "pretty:";
    }

    public List<MetadataBean> getMetadata()
    {
        return metadata;
    }

    public void setMetadata(List<MetadataBean> metadata)
    {
        this.metadata = metadata;
    }

    /*
     * public List<SelectItem> getTypesMenu() { List<SelectItem> list = new ArrayList<SelectItem>(); for (MdField mdf :
     * mdFields) { list.add(new SelectItem(mdf.getLabel())); } return list; }
     */
    public int getNumberOfProfiles()
    {
        if (profiles == null && profile != null)
            return 1;
        return this.profiles.size();
    }

    /*
     * public List<MdField> getMdFields() { return mdFields; } public void setMdFields(List<MdField> mdFields) {
     * this.mdFields = mdFields; }
     */
    public void setStatementMenu(List<SelectItem> statementMenu)
    {
        this.statementMenu = statementMenu;
    }

    public List<SelectItem> getStatementMenu()
    {
        return statementMenu;
    }

    public void setMdPosition(int mdPosition)
    {
        this.mdPosition = mdPosition;
    }

    public int getMdPosition()
    {
        return mdPosition;
    }
}
