package org.sonatype.maven.plugin.cobertura4it;

/*
 * PUT YOUR LICENSE HEADER HERE.
 */

import java.io.File;

import net.sourceforge.cobertura.coveragedata.CoverageDataFileHandler;
import net.sourceforge.cobertura.coveragedata.ProjectData;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * @goal check
 * @phase verify
 * @author <a href="mailto:velo.br@gmail.com">Marvin Froeder</a>
 */
public class CheckMojo
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
     * The average line coverage rate for all children in this container. This number will be an integer between 0 and
     * 100, inclusive.
     * 
     * @parameter expression="${cobertura.minimunLineCoverage}"
     */
    private String minimunLineCoverage;

    /**
     * The average branch coverage rate for all children in this container. This number will be an integer between 0 and
     * 100, inclusive.
     * 
     * @parameter expression="${cobertura.minimunBranchCoverage}"
     */
    private String minimunBranchCoverage;

    /**
     * Executes this mojo.
     * 
     * @throws MojoExecutionException If the reports could not be generated.
     */
    public void execute()
        throws MojoExecutionException
    {
        ProjectData coverageProjectData = new ProjectData();

        File coverage = new File( project.getBuild().getDirectory(), "/cobertura/cobertura.ser" );
        if ( coverage.exists() )
        {
            coverageProjectData.merge( CoverageDataFileHandler.loadCoverageData( coverage ) );
        }
        coverage = new File( project.getBasedir(), "cobertura.ser" );
        if ( coverage.exists() )
        {
            coverageProjectData.merge( CoverageDataFileHandler.loadCoverageData( coverage ) );
        }

        if ( coverageProjectData.getClasses().isEmpty() )
        {
            return;
        }

        if ( minimunLineCoverage != null )
        {
            int lineCoverageRate = (int) Math.round( coverageProjectData.getLineCoverageRate() * 100 );
            if ( lineCoverageRate < new Integer( minimunLineCoverage ) )
            {
                throw new MojoExecutionException( "Expected a line coverage of at least " + minimunLineCoverage
                    + "% but got " + lineCoverageRate + "%" );
            }
        }

        if ( minimunBranchCoverage != null )
        {
            int branchCoverageRate = (int) Math.round( coverageProjectData.getBranchCoverageRate() * 100 );
            if ( branchCoverageRate < new Integer( minimunBranchCoverage ) )
            {
                throw new MojoExecutionException( "Expected a branch coverage of at least " + minimunBranchCoverage
                    + "% but got " + branchCoverageRate + "%" );
            }
        }
    }
}
