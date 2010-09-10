package org.sonatype.maven.plugin.cobertura4it;

/*
 * PUT YOUR LICENSE HEADER HERE.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sourceforge.cobertura.coveragedata.CoverageDataFileHandler;
import net.sourceforge.cobertura.coveragedata.ProjectData;
import net.sourceforge.cobertura.reporting.ComplexityCalculator;
import net.sourceforge.cobertura.reporting.html.HTMLReport;
import net.sourceforge.cobertura.reporting.xml.SummaryXMLReport;
import net.sourceforge.cobertura.reporting.xml.XMLReport;
import net.sourceforge.cobertura.util.FileFinder;
import net.sourceforge.cobertura.util.Source;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;

/**
 * Generates human-readable reports from the previously collected coverage data. <strong>Note:</strong> Unlike the
 * related goal <code>emma4it</code>, this goal is not meant to participate in the site lifecycle but in the normal
 * build lifecycle after the tests have been run.
 * 
 * @goal report
 * @phase test
 * @author <a href="mailto:velo.br@gmail.com">Marvin Froeder</a>
 */
public class ReportMojo
    extends AbstractMojo
{

    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component
     */
    private ArtifactResolver resolver;

    /**
     * @parameter default-value="${localRepository}"
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @parameter default-value="${project.remoteArtifactRepositories}"
     * @readonly
     */
    private List<? extends ArtifactRepository> remoteRepositories;

    /**
     * The project's base directory.
     * 
     * @parameter default-value="${basedir}"
     * @readonly
     */
    private File baseDirectory;

    /**
     * The project's build directory.
     * 
     * @parameter default-value="${project.build.directory}"
     * @readonly
     */
    private File buildDirectory;

    /**
     * The location of the generated report files.
     * 
     * @parameter default-value="${project.reporting.outputDirectory}/coverage"
     */
    private File reportDirectory;

    /**
     * The (case-sensitive) names of the reports to be generated. Supported reports are <code>txt</code>,
     * <code>xml</code> and <code>html</code>. Defaults to <code>txt</code>, <code>xml</code> and <code>html</code>.
     * 
     * @parameter
     */
    private String[] formats;

    /**
     * The offline metadata and runtime coverage files to generate the reports from. Defaults to all <code>*.ec</code>,
     * <code>*.em</code> and <code>*.es</code> files within the project base directory and within any subdirectory of
     * the build directory.
     * 
     * @parameter
     */
    private FileSet[] dataSets;

    /**
     * An optional collection of source directories to be used for the HTML report.
     * 
     * @parameter
     */
    private FileSet[] sourceSets;

    /**
     * An optional collection of artifacts whose source attachments should be resolved for the HTML report.
     * 
     * @parameter
     */
    private ArtifactItem[] artifactItems;

    /**
     * Location of the file.
     * 
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

    /**
     * Encoding used to generate coverage report
     * 
     * @parameter expression="${project.build.sourceEncoding}"
     */
    private String coverageReportEncoding;

    /**
     * Executes this mojo.
     * 
     * @throws MojoExecutionException If the reports could not be generated.
     */
    public void execute()
        throws MojoExecutionException
    {
        File coverage = new File( project.getBuild().getDirectory(), "/cobertura/cobertura.ser" );
        ProjectData coverageProjectData = CoverageDataFileHandler.loadCoverageData( coverage );

        FileFinder finder = new FileFinder()
        {
            @Override
            public Source getSource( String fileName )
            {
                Source source = super.getSource( fileName );
                if ( source == null )
                {
                    source = super.getSource( fileName.replace( ".java", ".as" ) );
                }
                if ( source == null )
                {
                    source = super.getSource( fileName.replace( ".java", ".mxml" ) );
                }
                return source;
            }
        };

        List<File> sourcePath = collectSourcePath();

        for ( File dir : sourcePath )
        {
            finder.addSourceDirectory( dir.getAbsolutePath() );
        }

        ComplexityCalculator complexity = new ComplexityCalculator( finder );
        try
        {
            reportDirectory.mkdirs();

            List<String> format = Arrays.asList( getFormats() );
            if ( format.contains( "html" ) )
            {
                if ( StringUtils.isEmpty( coverageReportEncoding ) )
                {
                    coverageReportEncoding = "UTF-8";
                }
                new HTMLReport( coverageProjectData, reportDirectory, finder, complexity, coverageReportEncoding );
            }
            else if ( format.contains( "xml" ) )
            {
                new XMLReport( coverageProjectData, reportDirectory, finder, complexity );
            }
            else if ( format.contains( "summaryXml" ) )
            {
                new SummaryXMLReport( coverageProjectData, reportDirectory, finder, complexity );
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to write coverage report", e );
        }
    }

    private String[] getFormats()
    {
        if ( formats == null || formats.length <= 0 )
        {
            return new String[] { "txt", "xml", "html" };
        }
        return formats;
    }

    private List<File> collectSourcePath()
        throws MojoExecutionException
    {
        getLog().debug( "Collecting source directories" );

        List<File> sourcePath = new ArrayList<File>();

        if ( sourceSets != null && sourceSets.length > 0 )
        {
            for ( FileSet fileSet : sourceSets )
            {
                if ( fileSet.getDirectory() == null )
                {
                    throw new MojoExecutionException( "Missing base directory for source set " + fileSet );
                }
                else if ( !fileSet.getDirectory().isDirectory() )
                {
                    getLog().warn( "Ignored non-existing source set directory " + fileSet.getDirectory() );
                }
                else
                {
                    sourcePath.addAll( fileSet.scan( false, true ) );
                }
            }
        }

        if ( getLog().isDebugEnabled() )
        {
            for ( File path : sourcePath )
            {
                getLog().debug( "  " + path.getAbsolutePath() );
            }
        }

        return sourcePath;
    }

    private List<Artifact> resolveArtifacts()
    {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for ( ArtifactItem artifactItem : artifactItems )
        {
            Artifact artifact =
                artifactFactory.createArtifactWithClassifier( artifactItem.getGroupId(), artifactItem.getArtifactId(),
                    artifactItem.getVersion(), artifactItem.getType(), "sources" );
            try
            {
                resolver.resolve( artifact, remoteRepositories, localRepository );
            }
            catch ( AbstractArtifactResolutionException e )
            {
                getLog().warn( "Artifact " + artifact.toString() + " source not available at Maven repository" );
            }
            artifacts.add( artifact );
        }
        return artifacts;
    }

}
