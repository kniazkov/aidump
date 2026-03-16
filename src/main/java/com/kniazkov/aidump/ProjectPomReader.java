package com.kniazkov.aidump;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

public final class ProjectPomReader {

    private ProjectPomReader() {
    }

    public static Model.ProjectPom read(Path projectRoot) throws Exception {
        Path pomPath = projectRoot.resolve("pom.xml");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);

        var builder = factory.newDocumentBuilder();
        var document = builder.parse(pomPath.toFile());
        Element project = document.getDocumentElement();

        Model.ProjectPom pom = new Model.ProjectPom();

        pom.groupId = directChildText(project, "groupId");
        pom.artifactId = directChildText(project, "artifactId");
        pom.version = directChildText(project, "version");
        pom.name = directChildText(project, "name");
        pom.description = directChildText(project, "description");

        Element parent = directChild(project, "parent");
        if (parent != null) {
            if (isBlank(pom.groupId)) {
                pom.groupId = directChildText(parent, "groupId");
            }
            if (isBlank(pom.version)) {
                pom.version = directChildText(parent, "version");
            }
        }

        if (isBlank(pom.artifactId)) {
            pom.artifactId = projectRoot.getFileName().toString();
        }
        if (isBlank(pom.name)) {
            pom.name = pom.artifactId;
        }

        return pom;
    }

    private static Element directChild(Element parent, String tagName) {
        NodeList nodeList = parent.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String directChildText(Element parent, String tagName) {
        Element child = directChild(parent, tagName);
        if (child == null) {
            return null;
        }
        String value = child.getTextContent();
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
