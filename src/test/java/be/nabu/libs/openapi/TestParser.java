package be.nabu.libs.openapi;

import java.io.IOException;
import java.io.InputStream;

import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.swagger.api.SwaggerDefinition;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.properties.MinOccursProperty;
import junit.framework.TestCase;

public class TestParser extends TestCase {
//	public void testDuplicateDefinitionElement() throws IOException {
//		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-duplicate-definition-element.json")) {
//			SwaggerDefinition definition = new OpenApiParserv3().parse("test", input);
//			System.out.println("Namespaces: " + definition.getRegistry().getNamespaces());
//			for (ComplexType type : definition.getRegistry().getComplexTypes("test.types")) {
//				System.out.println("Found: " + type);
//			}
//			ComplexType complexType = definition.getRegistry().getComplexType("test.types", "image");
//			for (Element<?> element : TypeUtils.getAllChildren(complexType)) {
//				System.out.println(element.getName() + ": " + element.getType().getName());
//			}
//		}
//	}
	
	public void testAnyOfNullableVariants() throws IOException {
		try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("test-anyof-nullable.json")) {
			SwaggerDefinition definition = new OpenApiParserv3().parse("test", input);
			ComplexType nullableObject = definition.getRegistry().getComplexType("test.types", "nullableObject");
			assertNotNull(nullableObject);
			assertNull(nullableObject.getSuperType());
			assertNotNull(nullableObject.get("name"));
			assertNull(nullableObject.get("value"));
			assertEquals(Integer.valueOf(1), ValueUtils.getValue(MinOccursProperty.getInstance(), nullableObject.get("name").getProperties()));
			
			ComplexType baseObject = definition.getRegistry().getComplexType("test.types", "baseObject");
			assertNotNull(baseObject);
			assertNotNull(baseObject.get("value"));
			assertNull(baseObject.get("name"));
			assertEquals(Integer.valueOf(1), ValueUtils.getValue(MinOccursProperty.getInstance(), baseObject.get("value").getProperties()));
		}
	}
}
