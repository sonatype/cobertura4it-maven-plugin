package org.sonatype.maven.plugin.cobertura4it;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.sourceforge.cobertura.coveragedata.CoverageDataFileHandler;
import net.sourceforge.cobertura.coveragedata.ProjectData;
import net.sourceforge.cobertura.instrument.NonDummClassInstrumenter;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * @author <a href="mailto:velo.br@gmail.com">Marvin Froeder</a>
 * @goal instrument-artifact
 * @phase process-test-classes
 */
public class ArtifactInstrumenterMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     */
    private MavenProject project;

    /** @component */
    private ArtifactFactory artifactFactory;

    /** @component */
    private ArtifactResolver resolver;

    /** @parameter expression="${localRepository}" */
    private ArtifactRepository localRepository;

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    private List<? extends ArtifactRepository> remoteRepositories;

    /**
     * @parameter
     */
    private ArtifactItem[] artifactItems;

    /**
     * @parameter
     */
    private File[] jarFiles;

    /**
     * The collection of JAR files to instrument.
     * 
     * @parameter
     * @since 1.2
     */
    private FileSet[] jarSets;

    /**
     * // TODO need to figure out what are this about
     * 
     * @parameter
     */
    private Collection<Pattern> ignoreRegexes = new Vector<Pattern>();

    /**
     * // TODO need to figure out what are this about
     * 
     * @parameter
     */
    private Collection<Pattern> ignoreBranchesRegexes = new Vector<Pattern>();

    public void execute()
        throws MojoExecutionException
    {
        List<File> instrPath = collectInstrumentationPath();

        if ( instrPath.isEmpty() )
        {
            getLog().error( "Nothing found to instrument!" );
            return;
        }

        // InstrProcessor.OutMode outMode = InstrProcessor.OutMode.nameToMode( outputMode );
        // if ( outMode == null )
        // {
        // throw new MojoExecutionException( "invalid outputMode value: " + outputMode );
        // }

        File coberturaFolder = new File( project.getBuild().getDirectory(), "cobertura" );
        if ( !coberturaFolder.exists() )
        {
            coberturaFolder.mkdirs();
        }

        ProjectData projectData = new ProjectData();

        for ( File file : instrPath )
        {
            instrument( projectData, file );
        }

        CoverageDataFileHandler.saveCoverageData( projectData, new File( coberturaFolder, "cobertura.ser" ) );
    }

    private void instrument( ProjectData projectData, File file )
        throws MojoExecutionException
    {
        File original = null;
        try
        {
            original =
                File.createTempFile( FileUtils.basename( file.getName() ), "." + FileUtils.extension( file.getName() ) );

            FileUtils.copyFile( file, original );
            file.delete();

            ZipInputStream zipInput = new ZipInputStream( new FileInputStream( original ) );
            ZipOutputStream zipOutput = new ZipOutputStream( new FileOutputStream( file ) );

            try
            {
                ZipEntry entry;
                while ( ( entry = zipInput.getNextEntry() ) != null )
                {
                    if ( entry.isDirectory() )
                    {
                        continue;
                    }

                    zipOutput.putNextEntry( new ZipEntry( entry.getName() ) );

                    byte[] classContent = IOUtil.toByteArray( zipInput );
                    if ( entry.getName().endsWith( ".class" ) )
                    {
                        ClassReader cr = new ClassReader( classContent );
                        ClassWriter cw = new ClassWriter( ClassWriter.COMPUTE_MAXS );
                        NonDummClassInstrumenter cv =
                            new NonDummClassInstrumenter( projectData, cw, ignoreRegexes, ignoreBranchesRegexes );
                        cr.accept( cv, 0 );

                        if ( cv.isInstrumented() )
                        {
                            classContent = cw.toByteArray();
                        }
                    }

                    IOUtil.copy( classContent, zipOutput );

                    zipOutput.closeEntry();
                }
            }
            finally
            {
                IOUtil.close( zipInput );
                IOUtil.close( zipOutput );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
        finally
        {
            if ( original != null )
            {
                original.delete();
            }
        }
    }

    private List<File> collectInstrumentationPath()
        throws MojoExecutionException
    {
        getLog().debug( "Collecting JAR files" );

        List<File> instrPath = new ArrayList<File>();

        if ( artifactItems != null && artifactItems.length != 0 )
        {
            List<Artifact> artifacts = resolveArtifacts();
            instrPath.addAll( getPaths( artifacts ) );
        }

        if ( jarFiles != null )
        {
            for ( File jar : jarFiles )
            {
                if ( jar.exists() )
                {
                    instrPath.add( jar );
                }
                else
                {
                    getLog().warn( "JAR " + jar.getAbsolutePath() + " not found!" );
                }
            }
        }

        if ( jarSets != null && jarSets.length > 0 )
        {
            for ( FileSet fileSet : jarSets )
            {
                if ( fileSet.getDirectory() == null )
                {
                    throw new MojoExecutionException( "Missing base directory for JAR set " + fileSet );
                }
                else if ( !fileSet.getDirectory().isDirectory() )
                {
                    getLog().warn( "Ignored non-existing JAR set directory " + fileSet.getDirectory() );
                }
                else
                {
                    instrPath.addAll( fileSet.scan( true, false ) );
                }
            }
        }

        if ( getLog().isDebugEnabled() )
        {
            for ( File path : instrPath )
            {
                getLog().debug( "  " + path.getAbsolutePath() );
            }
        }

        return instrPath;
    }

    private List<File> getPaths( List<Artifact> artifacts )
    {
        List<File> paths = new ArrayList<File>();
        for ( Artifact artifact : artifacts )
        {
            paths.add( artifact.getFile() );
        }
        return paths;
    }

    private List<Artifact> resolveArtifacts()
        throws MojoExecutionException
    {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for ( ArtifactItem artifactItem : artifactItems )
        {
            Artifact artifact =
                artifactFactory.createArtifactWithClassifier( artifactItem.getGroupId(), artifactItem.getArtifactId(),
                                                              artifactItem.getVersion(), artifactItem.getType(),
                                                              artifactItem.getClassifier() );
            try
            {
                resolver.resolve( artifact, remoteRepositories, localRepository );
            }
            catch ( AbstractArtifactResolutionException e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }
            artifacts.add( artifact );
        }
        return artifacts;
    }

}
