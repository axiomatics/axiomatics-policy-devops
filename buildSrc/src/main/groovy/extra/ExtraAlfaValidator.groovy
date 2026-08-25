package extra

import com.axiomatics.alfa.xacmlDsl.NamespaceDeclaration
import com.axiomatics.alfa.xacmlDsl.impl.AttributeImpl
import com.axiomatics.alfa.xacmlDsl.impl.NamespaceDeclarationImpl
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.nodemodel.ICompositeNode
import org.eclipse.xtext.nodemodel.util.NodeModelUtils

import java.util.stream.Collectors

class ExtraAlfaValidator {
    static void validate(Resource resource) {
       String file = resource.getURI().toFileString()
        EList<EObject> contents = resource.getContents()
        contents.forEach(it -> visit(null,file, it))

    }

    static void visit(List<NamespaceDeclaration> namespaces, String file, EObject object) {
        object.eContents().forEach(it -> visit(namespaces, file, it))
    }

    static void visit(List<NamespaceDeclaration> namespaces, String file, NamespaceDeclarationImpl ns) {

        ns.eContents().forEach(it -> {
            List<NamespaceDeclaration> localNamespaces = new ArrayList<>();
            if (namespaces != null) {
                localNamespaces.addAll(namespaces);
            }
            localNamespaces.add(ns);
            visit(localNamespaces, file, it)
        })
    }

    private static final List<String> hardCodedAttributeIds = List.of(
            "urn:oasis:names:tc:xacml:1.0:environment:current-date",
            "urn:oasis:names:tc:xacml:1.0:environment:current-dateTime",
            "urn:oasis:names:tc:xacml:1.0:environment:current-time");

    static void visit(List<NamespaceDeclaration> namespaces,String file,  AttributeImpl attribute) {
        String namespaceString =  namespaces.isEmpty()? "" : namespaces.stream()
                .map(it -> it.name)
                .collect(Collectors.joining('.','','.')).toString()
        String fqn = namespaceString + attribute.name

        if (hardCodedAttributeIds.contains(attribute.getUri())) {
            return
        }

        ICompositeNode node = NodeModelUtils.getNode(attribute);

        if (false == attribute.getUri().equals(fqn)) {
            throw new RuntimeException("Attribute id and name mismatches for some attributes in your alfa. " +
                    "You are strongly recommended to have id and name equal for attributes in file attributes.yaml. Violating attribute is '" +
                    fqn + "' != '" + attribute.getUri() + "' in " +file+ ":"+node.startLine + ". Suggestion: Make id and name equal, remove 'standard-attributes.alfa' file if it exists in your src directory or you can also turn off this extra check by setting adding 'extraAttributeIdCheck' false in alfa section of build.gradle.")

        }
    }
}
