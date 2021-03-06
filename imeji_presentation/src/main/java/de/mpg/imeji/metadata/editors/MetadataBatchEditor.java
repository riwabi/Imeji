/**
 * License: src/main/resources/license/escidoc.license
 */

package de.mpg.imeji.metadata.editors;

import java.util.ArrayList;
import java.util.List;

import de.mpg.jena.vo.Image;
import de.mpg.jena.vo.ImageMetadata;
import de.mpg.jena.vo.MetadataProfile;
import de.mpg.jena.vo.Statement;

public class MetadataBatchEditor extends MetadataEditor 
{
	private List<Image> originalImages;
	
	public MetadataBatchEditor(List<Image> images, MetadataProfile profile,	Statement statement) 
	{
		super(images, profile, statement);
	}

	@Override
	public void initialize() 
	{
		originalImages = images;
		this.images = new ArrayList<Image>();
		this.images.add(new Image());
		this.images.get(0).getMetadataSet().getMetadata().add(newMetadata());
	}
	

	@Override
	public boolean prepareUpdate() 
	{
		if (images.size() == 0)
		{
			return false;
		}

		ImageMetadata md = images.get(0).getMetadataSet().getMetadata().iterator().next();
		for (Image im: originalImages)
		{
			im.getMetadataSet().getMetadata().add(md);
		}
		images = originalImages;
		return true;
	}
	

	@Override
	public boolean validateMetadataofImages() 
	{
//		for (Image im : images)
//		{
//			validator = new Validator(im.getMetadata(), profile);
//			if (!(validator.valid()))
//			{
//				return false;
//			}
//		}
		return true;
	}
	

	@Override
	public void addMetadata(int imagePos, int metadataPos) {
		// TODO Auto-generated method stub		
	}

	@Override
	public void addMetadata(Image image, int metadataPos)
	{
		if (metadataPos <= image.getMetadataSet().getMetadata().size()) 
		{
			List<ImageMetadata> newList = new ArrayList<ImageMetadata>();
			newList.add(metadataPos, newMetadata());
			image.getMetadataSet().getMetadata().addAll(newList);
			//image.getMetadata().add(metadataPos, newMetadata());
		}
	}

	@Override
	public void removeMetadata(int imagePos, int metadataPos) {
		// TODO Auto-generated method stub		
	}

	@Override
	public void removeMetadata(Image image, int metadataPos) {
		// TODO Auto-generated method stub
	}

}
