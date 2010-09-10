package net.sourceforge.cobertura.instrument;

import java.util.Collection;

import net.sourceforge.cobertura.coveragedata.ProjectData;

import org.objectweb.asm.ClassVisitor;

public class NonDummClassInstrumenter
    extends ClassInstrumenter
{

    public NonDummClassInstrumenter( ProjectData projectData, ClassVisitor cv, Collection ignoreRegexs,
                                     Collection ignoreBranchesRegexes )
    {
        super( projectData, cv, ignoreRegexs, ignoreBranchesRegexes );
    }

}
