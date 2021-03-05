package net.foragerr.jmeter.gradle.plugins.utils

import groovy.transform.CompileStatic

import javax.xml.transform.Transformer
import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import java.text.SimpleDateFormat

@CompileStatic
class ReportTransformer {

    private final Transformer transformer

    ReportTransformer(InputStream xsl) throws TransformerConfigurationException {
        TransformerFactory tFactory = TransformerFactory.newInstance()
        if (xsl == null) {
            throw new NullPointerException('the input stream for the xsl was null.')
        }
        this.transformer = tFactory.newTransformer(new StreamSource(xsl))
    }

    void transform(File inputFile, File outputFile, String reportTitle) throws FileNotFoundException, TransformerException {
        transformer.setParameter('reportTitle', reportTitle)
        transformer.setParameter('dateReport', new SimpleDateFormat('yyyy/MM/dd HH:mm:ss').format(Calendar.getInstance().getTime()))
        transformer.transform(
                new StreamSource(inputFile),
                new StreamResult(new FileOutputStream(outputFile)))
    }

}
