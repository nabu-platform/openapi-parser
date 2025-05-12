package be.nabu.libs.openapi;

import java.io.IOException;
import java.io.InputStream;

import be.nabu.libs.swagger.api.SwaggerDefinition;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import junit.framework.TestCase;

public class TestParser extends TestCase {
	public void testDuplicateDefinitionElement() throws IOException {
		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-duplicate-definition-element.json")) {
			SwaggerDefinition definition = new OpenApiParserv3().parse("test", input);
			System.out.println("Namespaces: " + definition.getRegistry().getNamespaces());
			for (ComplexType type : definition.getRegistry().getComplexTypes("test.types")) {
				System.out.println("Found: " + type);
			}
			ComplexType complexType = definition.getRegistry().getComplexType("test.types", "image");
			for (Element<?> element : TypeUtils.getAllChildren(complexType)) {
				System.out.println(element.getName() + ": " + element.getType().getName());
			}
		}
	}
}
