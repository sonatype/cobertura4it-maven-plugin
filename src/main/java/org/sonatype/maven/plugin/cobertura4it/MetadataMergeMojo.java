package org.sonatype.maven.plugin.cobertura4it;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.cobertura.coveragedata.CoverageDataFileHandler;
import net.sourceforge.cobertura.coveragedata.ProjectData;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * @goal merge
 * @author <a href="mailto:velo.br@gmail.com">Marvin Froeder</a>
 */
public class MetadataMergeMojo
    extends AbstractMojo
{

    /**
     * Location of the file.
     * 
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

    /**
     * @parameter default-value="${project.build.testOutputDirectory}"
     */
    private File searchPath;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !searchPath.isDirectory() )
        {
            throw new MojoExecutionException( "SearchPath " + searchPath + " not found." );
        }

        merge( "cobertura.ser" );
    }

    private void merge( String metadataFile )
    {
        getLog().info( "Merging " + metadataFile );
        List<File> paths = getMetadataPaths( metadataFile );
        if ( paths.isEmpty() )
        {
            getLog().error( "coverage.ec metadata not found." );
            return;
        }

        File output = new File( project.getBuild().getDirectory(), "/cobertura/" + metadataFile );
        ProjectData projectData = new ProjectData();

        for ( File file : paths )
        {
            projectData.merge( CoverageDataFileHandler.loadCoverageData( file ) );
        }

        output.getParentFile().mkdirs();

        CoverageDataFileHandler.saveCoverageData( projectData, output );
    }

    private List<File> getMetadataPaths( String metadataFile )
    {
        DirectoryScanner scan = new DirectoryScanner();
        scan.setBasedir( searchPath );
        scan.setIncludes( new String[] { "**/" + metadataFile } );
        scan.addDefaultExcludes();
        scan.scan();

        List<File> paths = new ArrayList<File>();
        for ( String path : scan.getIncludedFiles() )
        {
            paths.add( new File( searchPath, path ) );
        }

        return paths;
    }

}
