package be.nabu.libs.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.swagger.api.SwaggerDefinition;
import be.nabu.libs.swagger.api.SwaggerMethod;
import be.nabu.libs.swagger.api.SwaggerParameter;
import be.nabu.libs.swagger.api.SwaggerResponse;
import be.nabu.libs.swagger.api.SwaggerParameter.ParameterLocation;
import be.nabu.libs.swagger.api.SwaggerPath;
import be.nabu.libs.swagger.api.SwaggerRequest;
import be.nabu.libs.swagger.parser.MarshallableSimpleTypeExtension;
import be.nabu.libs.swagger.parser.SimpleTypeExtension;
import be.nabu.libs.swagger.parser.SwaggerDefinitionImpl;
import be.nabu.libs.swagger.parser.SwaggerMethodImpl;
import be.nabu.libs.swagger.parser.SwaggerParameterImpl;
import be.nabu.libs.swagger.parser.SwaggerParser;
import be.nabu.libs.swagger.parser.SwaggerPathImpl;
import be.nabu.libs.swagger.parser.SwaggerRequestImpl;
import be.nabu.libs.swagger.parser.SwaggerResponseImpl;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeRegistryImpl;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Marshallable;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.api.ModifiableType;
import be.nabu.libs.types.api.ModifiableTypeRegistry;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.CollectionFormat;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.TypeBaseUtils;
import be.nabu.libs.types.base.UUIDFormat;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.map.MapContent;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.MaxExclusiveProperty;
import be.nabu.libs.types.properties.MaxInclusiveProperty;
import be.nabu.libs.types.properties.MaxLengthProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinExclusiveProperty;
import be.nabu.libs.types.properties.MinInclusiveProperty;
import be.nabu.libs.types.properties.MinLengthProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.PatternProperty;
import be.nabu.libs.types.properties.TimezoneProperty;
import be.nabu.libs.types.properties.UUIDFormatProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;

@SuppressWarnings("unchecked")
public class OpenApiParserv3 {

	private TimeZone timezone;
	// for some reason some people generate as swagger where the format is set to "uuid" but it is actually not a valid uuid...
	// for instance sendgrid for some reason uses a valid uuid but prepends it with "d-" making it invalid...
	private boolean allowUuid = true;
	
	private UUIDFormat uuidFormat;
	
	public static void main(String...args) throws IOException, ParseException {
		URL url = new URL("https://api.hubspot.com/api-catalog-public/v1/apis/crm/v3/objects");
		InputStream openStream = url.openStream();
		try {
			OpenApiParserv3 openApiParserv3 = new OpenApiParserv3();
			SwaggerDefinition parsed = openApiParserv3.parse("test", openStream);
			System.out.println(openApiParserv3.references);
			System.out.println("operations: " + parsed.getPaths());
		}
		finally {
			openStream.close();
		}
	}
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	// keep a map so we can keep track of all the relevant references
	private Map<String, Object> references = new HashMap<String, Object>();
	
	public SwaggerDefinition parse(String id, InputStream input) {
		MapContent content = SwaggerParser.parseJson(input);
		return parse(id, content);
	}
	
	public SwaggerDefinition parse(String id, MapContent content) {
		SwaggerDefinitionImpl definition = new SwaggerDefinitionImpl(id);
		definition.setRegistry(new TypeRegistryImpl());
		try {
			// first parse the components
			Object componentContent = ((Map<String, Object>) content.getContent()).get("components");
			if (componentContent != null) {
				parseComponents(definition, (MapContent) componentContent);
			}
			
			Object pathContent = ((Map<String, Object>) content.getContent()).get("paths");
			if (pathContent != null) {
				definition.setPaths(parsePaths(definition, (MapContent) pathContent));
			}
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
		return definition;
	}
	
	// parse the components section
	private void parseComponents(SwaggerDefinitionImpl definition, ComplexContent content) throws ParseException {
		// we want to parse schemas first, they are at the core of all definnitions
		Object schemas = content.get("schemas");
		if (schemas != null) {
			parseComponentSchemas(definition, (ComplexContent) schemas, true);
			parseComponentSchemas(definition, (ComplexContent) schemas, false);
		}
		// headers can be referenced by things like responses
		Object headers = content.get("headers");
		if (headers != null) {
			parseComponentHeaders(definition, (ComplexContent) headers, "#/components/headers");
		}
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			Object object = content.get(child.getName());
			if (child.getName().equals("responses")) {
				parseComponentResponses(definition, (ComplexContent) object, "#/components/responses");
			}
			else if (child.getName().equals("parameters")) {
				parseComponentParameters(definition, (ComplexContent) object, "#/components/parameters");
			}
			else if (child.getName().equals("requestBodies")) {
				parseComponentRequestBodies(definition, (ComplexContent) object, "#/components/requestBodies");
			}
			else if (child.getName().equals("securitySchemes")) {
				parseComponentSecuritySchemes(definition, (ComplexContent) object);
			}
			else if (child.getName().equals("examples")) {
				logger.debug("Component examples are ignored during parsing");
			}
			else if (child.getName().equals("links")) {
				logger.debug("Component links are ignored during parsing");
			}
			else if (child.getName().equals("callbacks")) {
				logger.warn("Component callbacks are ignored during parsing");
			}
			else if (!child.getName().equals("schemas") && !child.getName().equals("headers")) {
				logger.warn("Unknown element found in components: " + child.getName());
			}
		}
	}
	private void parseComponentSchemas(SwaggerDefinitionImpl definition, ComplexContent content, boolean defineOnly) throws ParseException {
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			parseType((ModifiableTypeRegistry) definition.getRegistry(), definition.getId() + ".types", child.getName(), (ComplexContent) content.get(child.getName()), "#/components/schemas", defineOnly);
		}		
	}

	/**
	 * The referencePath points to a unique location where we can reference this from
	 * before we parse everything, make sure we have at least referential knowledge of all possible elements
 	 * this allows for out-of-order definition
	 */
	@SuppressWarnings("rawtypes")
	private Type parseType(ModifiableTypeRegistry registry, String baseId, String name, ComplexContent content, String referencePath, boolean defineOnly) throws ParseException {
		Object typeString = content.get("type");
		/**
		 * The sendgrid swagger contained 47 instances of:
		 *  "type": [
		        "null",
		        "string"
		    ],
		    while not allowed by the spec, there does not seem a downside in adding (dubious) support for this
		 */
		if (typeString instanceof List) {
			for (Object single : (List<?>) typeString) {
				if (single != null && !"null".equals(single)) {
					typeString = single;
					break;
				}
			}
		}
		// end of sendgrid "fix"

		Type type = null;
		
		String cleanedUpName = SwaggerParser.cleanup(name);
		String typeId = baseId + "." + cleanedUpName;
		
		// if it is defined, check the registry if we already added it
		if (referencePath != null) {
			type = registry.getTypeById(typeId);
		}
		
		boolean alreadyRegistered = type != null; 
		
		List<Value<?>> values = new ArrayList<Value<?>>();
		
		// if the type is "object", we create a structure, otherwise a simple type
		if (typeString == null || typeString.equals("object")) {
			if (type == null) {
				if (referencePath != null) {
					type = new DefinedStructure();
					((DefinedStructure) type).setId(typeId);
				}
				else {
					type = new Structure();
				}
				((Structure) type).setName(cleanedUpName);
				if (referencePath != null && !alreadyRegistered) {
					registry.register((ComplexType) type);
				}
			}
			// if we want to do more than define in this cycle, let's proceed with parsing
			// in the define only phase, we want to allow references to be created without actually having parsed it yet (definitions can be out-of-order from a parsing perspective)
			if (!defineOnly) {
				Structure structure = (Structure) type;
				if (content.get("allOf") != null) {
					List<Object> allOf = (List<Object>) content.get("allOf");
					// first find the root we are extending (if any)
					for (Object single : allOf) {
						Map<String, Object> singleMap = ((MapContent) single).getContent();
						if (singleMap.containsKey("$ref")) {
							Type superType = findType((String) singleMap.get("$ref"));
							if (superType == null) {
								throw new ParseException("Can not find super type: " + singleMap.get("$ref"), 1);
							}
							// the first ref will be mapped as a supertype
							if (structure.getSuperType() == null) {
								structure.setSuperType(superType);
							}
							// other refs are expanded in it
							else {
								if (superType instanceof ComplexType) {
									for (Element<?> child : TypeUtils.getAllChildren((ComplexType) superType)) {
										structure.add(TypeBaseUtils.clone(child, structure));
									}
								}
								else {
									throw new ParseException("Can only unfold a complex type when doing multiple extensions", 2);
								}
							}
						}
					}
					// find all non-reference extensions
					for (Object single : allOf) {
						Map<String, Object> singleMap = ((MapContent) single).getContent();
						if (!singleMap.containsKey("$ref")) {
							if (((MapContent) single).get("properties") != null) {
								parseComplexType(structure, registry, (ComplexContent) ((MapContent) single).get("properties"), (List<String>) ((MapContent) single).get("required"));
							}
							else {
								logger.warn("Could not find $ref or properties for allOf " + name);
							}
						}
					}
				}
				else {
					MapContent properties = (MapContent) content.get("properties");
					if (properties != null) {
						parseComplexType(structure, registry, properties, (List<String>) ((MapContent) content).get("required"));
					}
				}
				// if the structure has no fields, make it a generic object instead (unless it is a root, because then it is referred to by other types and must be resolvable)
				if (referencePath == null && TypeUtils.getAllChildren(structure).isEmpty()) {
					type = new BeanType<Object>(Object.class);
				}
			}
		}
		else if (typeString.equals("array")) {
			// the actual type is in "items"
			MapContent items = (MapContent) content.get("items");
			
			Type parsedDefinedType;
			
			// if no items are specified, we assume a string array
			if (items == null) {
				parsedDefinedType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class);
			}
			else {
				parsedDefinedType = items.get("$ref") == null 
					? parseType(registry, null, name, items, null, false)
					: findType((String) items.get("$ref"));
			}
			
			// we need to extend it to add the messed up max/min occurs properties...
			// this extension does not need to be registered globally (in general)
			// nabu allows for casting in parents to children, so at runtime you can create a parent instance and cast it to the child
			// so this will work transparently...
			if (parsedDefinedType instanceof Marshallable) {
				type = new MarshallableSimpleTypeExtension(
					referencePath == null && parsedDefinedType instanceof DefinedType ? ((DefinedType) parsedDefinedType).getId() : typeId, 
					baseId, 
					name, 
					(SimpleType<?>) parsedDefinedType
				);
			}
			else if (parsedDefinedType instanceof SimpleType) {
				type = new SimpleTypeExtension(
					referencePath == null && parsedDefinedType instanceof DefinedType ? ((DefinedType) parsedDefinedType).getId() : typeId, 
					baseId, 
					name, 
					(SimpleType<?>) parsedDefinedType
				);
			}
			else {
				Structure structure = referencePath == null ? new Structure() : new DefinedStructure();
				structure.setSuperType(parsedDefinedType);
				structure.setName(cleanedUpName);
				if (referencePath != null) {
					structure.setNamespace(baseId);
					((DefinedStructure) structure).setId(typeId);
				}
//				else {
//					((DefinedStructure).setId(parsedDefinedType instanceof DefinedType ? ((DefinedType) parsedDefinedType).getId() : typeId);
//				}
				type = structure;
			}
			
			Number maxOccurs = (Number) content.get("maxItems");
			Number minOccurs = (Number) content.get("minItems");
			
			int defaultMaxOccurs = type instanceof SimpleType && ((SimpleType<?>) type).getInstanceClass().equals(byte[].class) ? 1 : 0;
			values.add(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), maxOccurs == null ? defaultMaxOccurs : maxOccurs.intValue()));
			values.add(new ValueImpl<Integer>(MinOccursProperty.getInstance(), minOccurs == null ? 0 : minOccurs.intValue()));
		}
		// simple type
		else {
			SimpleType<?> simpleType;
			// note that the "format" is only indicative, there are a few listed formats you should use
			// but apart from that you can use any format you choose
			String format = (String) content.get("format");
			if (typeString.equals("number")) {
				if (format == null || format.equals("double")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Double.class);
				}
				else if (format.equals("float")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Float.class);
				}
				else {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Double.class);
					values.add(new ValueImpl<String>(CommentProperty.getInstance(), "Unsupported Format: " + format));
				}
			}
			else if (typeString.equals("integer")) {
				if (format == null || format.equals("int32")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Integer.class);
				}
				else if (format.equals("int64")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class);
				}
				else if (format.equalsIgnoreCase("bigInteger")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(BigInteger.class);
				}
				else if (format.equalsIgnoreCase("bigDecimal")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(BigDecimal.class);
				}
				else {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class);
					values.add(new ValueImpl<String>(CommentProperty.getInstance(), "Unsupported Format: " + format));
				}
			}
			else if (typeString.equals("boolean")) {
				simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Boolean.class);
			}
			else if (typeString.equals("file")) {
				simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class);
			}
			// we put all unrecognized types into a string as well to be lenient (e.g. the github swagger had a type "null", presumably an error in generation...)
			else { // if (type.equals("string"))
				if (!typeString.equals("string") || format == null || format.equals("string") || format.equals("password")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class);
				}
				// unofficial, added for digipolis
				else if (format.equals("uri")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(URI.class);
				}
				else if (format.equals("byte")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(byte[].class);
				}
				else if (format.equals("binary")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(InputStream.class);
				}
				else if (format.equals("date")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Date.class);
					values.add(new ValueImpl<String>(FormatProperty.getInstance(), "date"));
					if (timezone != null) {
						values.add(new ValueImpl<TimeZone>(TimezoneProperty.getInstance(), timezone));
					}
				}
				// don't set any additional properties for dateTime, this is the default and we avoid generating some unnecessary simple types
				else if (format.equals("date-time")) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Date.class);
					if (timezone != null) {
						values.add(new ValueImpl<TimeZone>(TimezoneProperty.getInstance(), timezone));
					}
				}
				else if (format.equals("uuid") && allowUuid) {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(UUID.class);
					if (uuidFormat != null) {
						values.add(new ValueImpl<UUIDFormat>(UUIDFormatProperty.getInstance(), uuidFormat));	
					}
				}
				else {
					simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class);
					values.add(new ValueImpl<String>(CommentProperty.getInstance(), "Unsupported Format: " + format));
				}
			}
			
			Boolean required = (Boolean) content.get("required");
			// the default value for required (false) is the opposite of the minoccurs
			// if it is not specified, do we want it to be inserted?
			if (required == null || (required != null && !required)) {
				values.add(new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
			}
			type = new MarshallableSimpleTypeExtension(typeId, baseId, name, simpleType);
		}
		
		// common stuff
		String description = (String) content.get("description");
		if (description != null) {
			values.add(new ValueImpl<String>(CommentProperty.getInstance(), description));
		}
		Number maximum = (Number) content.get("maximum");
		Boolean exclusiveMaximum = (Boolean) content.get("exclusiveMaximum");
		if (maximum != null) {
			Object convert = ConverterFactory.getInstance().getConverter().convert(maximum, ((SimpleType<?>) type).getInstanceClass());
			values.add(new ValueImpl(exclusiveMaximum == null || !exclusiveMaximum ? new MaxInclusiveProperty() : new MaxExclusiveProperty(), convert));
		}
		
		Number minimum = (Number) content.get("minimum");
		Boolean exclusiveMinimum = (Boolean) content.get("exclusiveMinimum");
		if (minimum != null) {
			Object convert = ConverterFactory.getInstance().getConverter().convert(minimum, ((SimpleType<?>) type).getInstanceClass());
			values.add(new ValueImpl(exclusiveMinimum == null || !exclusiveMinimum ? new MinInclusiveProperty() : new MinExclusiveProperty(), convert));
		}
		
		Number maxLength = (Number) content.get("maxLength");
		if (maxLength != null) {
			values.add(new ValueImpl(MaxLengthProperty.getInstance(), maxLength.intValue()));
		}
		
		Number minLength = (Number) content.get("minLength");
		if (minLength != null) {
			values.add(new ValueImpl(MinLengthProperty.getInstance(), minLength.intValue()));
		}
		
		List<?> enumValues = (List<?>) content.get("enum");
		if (enumValues != null && !enumValues.isEmpty()) {
			values.add(new ValueImpl(new EnumerationProperty(), enumValues));
		}
		
		String pattern = (String) content.get("pattern");
		if (pattern != null) {
			values.add(new ValueImpl(PatternProperty.getInstance(), pattern));
		}
		
		// if we have a simple type with no additional settings and it is not a root definition, unwrap it to the original simple type
		if (values.isEmpty() && referencePath == null && type instanceof SimpleType) {
			return type.getSuperType();
		}
		else {
			((ModifiableType) type).setProperty(values.toArray(new Value[values.size()]));
		}
		
		// if we have a reference path, register the type for future lookups
		if (referencePath != null) {
			references.put(referencePath + "/" + name, type);
		}
		return type;
	}
	private Type findType(String reference) throws ParseException {
		Type type = null;
		if (this.references.containsKey(reference)) {
			type = (Type) this.references.get(reference);
		}
		if (type == null) {
			logger.warn("Could not resolve reference: " + reference);
			type = BeanResolver.getInstance().resolve(Object.class);
		}
		return type;
	}
	@SuppressWarnings({ "rawtypes" })
	private void parseComplexType(ModifiableComplexType structure, ModifiableTypeRegistry registry, ComplexContent properties, List<String> required) throws ParseException {
		if (properties != null) {
			for (Element<?> child : TypeUtils.getAllChildren(properties.getType())) {
				ComplexContent childContent = (ComplexContent) properties.get(child.getName());
				String reference = (String) childContent.get("$ref");
				Type childType;
				if (reference != null) {
					childType = findType(reference);
				}
				else {
					childType = parseType(registry, null, child.getName(), childContent, null, false);
				}
				if (childType instanceof SimpleType) {
					structure.add(new SimpleElementImpl(child.getName(), (SimpleType<?>) childType, structure, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required == null || !required.contains(child.getName()) ? 0 : 1)));
				}
				// if we have a complex type that extends "Object" and has no other properties, unwrap it
				// ideally the parseDefinedType should probably be updated to parseElement or something so we don't need to extend types to transfer information...
				else if (childType instanceof ComplexType && TypeUtils.getAllChildren((ComplexType) childType).isEmpty() && ((ComplexType) childType).getSuperType() instanceof BeanType && ((BeanType<?>) ((ComplexType) childType).getSuperType()).getBeanClass().equals(Object.class)) {
					ComplexElementImpl element = new ComplexElementImpl(child.getName(), (ComplexType) childType.getSuperType(), structure, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required == null || !required.contains(child.getName()) ? 0 : 1));
					// inherit properties like maxOccurs
					Integer maxOccurs = ValueUtils.getValue(MaxOccursProperty.getInstance(), childType.getProperties());
					if (maxOccurs != null) {
						element.setProperty(new ValueImpl<Integer>(MaxOccursProperty.getInstance(), maxOccurs));
					}
					structure.add(element);				
				}
				else {
					structure.add(new ComplexElementImpl(child.getName(), (ComplexType) childType, structure, new ValueImpl<Integer>(MinOccursProperty.getInstance(), required == null || !required.contains(child.getName()) ? 0 : 1)));
				}
			}
		}
	}
	
	private List<SwaggerResponse> parseComponentResponses(SwaggerDefinitionImpl definition, ComplexContent content, String referencePath) throws ParseException {
		List<SwaggerResponse> responses = new ArrayList<SwaggerResponse>();
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			responses.add(parseComponentResponse(definition, child.getName(), (ComplexContent) content.get(child.getName()), referencePath));
		}
		return responses;
	}
	
	private SwaggerResponseImpl parseComponentResponse(SwaggerDefinitionImpl definition, String name, ComplexContent content, String referencePath) throws ParseException {
		SwaggerResponseImpl response = new SwaggerResponseImpl();
		if (name.matches("^[0-9]+$")) {
			response.setCode(Integer.parseInt(name));
		}
		response.setDescription((String) content.get("description"));
		ComplexContent definitionContent = (ComplexContent) content.get("content");
		if (definitionContent != null) {
			// TODO: currently we assume (as one normally would...?) that all the content types return the same data, so we only parse the first one and just keep track of the other content types that are allowed
			List<String> produces = new ArrayList<String>();
			for (Element<?> contentType : TypeUtils.getAllChildren(definitionContent.getType())) {
				ComplexContent contentTypeContent = (ComplexContent) definitionContent.get(contentType.getName());
				if (response.getElement() == null && contentTypeContent != null) {
					ComplexContent schema = (ComplexContent) contentTypeContent.get("schema");
					if (schema != null) {
						response.setElement(parseSchemaPart(definition, name, schema));
					}
				}
				produces.add(contentType.getName());
			}
			response.setProduces(produces);
		}
		ComplexContent headerContent = (ComplexContent) content.get("headers");
		if (headerContent != null) {
			// local header don't have a reference path
			response.setHeaders(parseComponentHeaders(definition, headerContent, null));
		}
		if (response != null && referencePath != null) {
			references.put(referencePath + "/" + name, response);
		}
		return response;
	}

	private List<SwaggerRequest> parseComponentRequestBodies(SwaggerDefinitionImpl definition, ComplexContent content, String referencePath) throws ParseException {
		List<SwaggerRequest> requests = new ArrayList<SwaggerRequest>();
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			requests.add(parseComponentRequestBody(definition, child.getName(), (ComplexContent) content.get(child.getName()), referencePath));
		}
		return requests;
	}
	private SwaggerRequest parseComponentRequestBody(SwaggerDefinitionImpl definition, String name, ComplexContent content, String referencePath) throws ParseException {
		SwaggerRequestImpl request = new SwaggerRequestImpl();
		request.setDescription((String) content.get("description"));
		Object requiredContent = content.get("allowReserved");
		// Determines whether the parameter value SHOULD allow reserved characters, as defined by RFC3986 :/?#[]@!$&'()*+,;= to be included without percent-encoding. This property only applies to parameters with an in value of query. The default value is false.
		boolean required = requiredContent != null && 
			((requiredContent instanceof Boolean && (Boolean) requiredContent)
				|| (requiredContent instanceof String && requiredContent.equals("true")));
		request.setRequired(required);
		ComplexContent definitionContent = (ComplexContent) content.get("content");
		if (definitionContent != null) {
			// TODO: currently we assume (as one normally would...?) that all the content types return the same data, so we only parse the first one and just keep track of the other content types that are allowed
			List<String> consumes = new ArrayList<String>();
			for (Element<?> contentType : TypeUtils.getAllChildren(definitionContent.getType())) {
				ComplexContent contentTypeContent = (ComplexContent) definitionContent.get(contentType.getName());
				if (request.getElement() == null && contentTypeContent != null) {
					ComplexContent schema = (ComplexContent) contentTypeContent.get("schema");
					if (schema != null) {
						request.setElement(parseSchemaPart(definition, name, schema));
					}
				}
				consumes.add(contentType.getName());
			}
			request.setConsumes(consumes);
		}
		if (request != null && referencePath != null) {
			references.put(referencePath + "/" + name, request);
		}
		return request;
	}
	
	@SuppressWarnings("rawtypes")
	private Element<?> parseSchemaPart(SwaggerDefinitionImpl definition, String name, ComplexContent schema) throws ParseException {
		Type parsedType = parseType((ModifiableTypeRegistry) definition.getRegistry(), definition.getId(), name, schema, null, false);
		if (parsedType instanceof ComplexType) {
			return new ComplexElementImpl(SwaggerParser.cleanup(name), (ComplexType) parsedType, null);
		}
		else if (parsedType instanceof SimpleType) {
			return new SimpleElementImpl(SwaggerParser.cleanup(name), (SimpleType<?>) parsedType, null);
		}
		else {
			throw new ParseException("Could not parse schema of response '" + name + "': " + parsedType, 0);
		}
	}
	private List<SwaggerParameter> parseComponentParameters(SwaggerDefinitionImpl definition, ComplexContent content, String referencePath) throws ParseException {
		List<SwaggerParameter> parameters = new ArrayList<SwaggerParameter>();
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			parameters.add(parseComponentParameter(definition, child.getName(), (ComplexContent) content.get(child.getName()), referencePath));
		}
		return parameters;
	}
	// for parameters the name is only relevant IF it has a global reference path
	// the actual name of the parameter is a value in the definition
	private SwaggerParameterImpl parseComponentParameter(SwaggerDefinitionImpl definition, String name, ComplexContent content, String referencePath) throws ParseException {
		SwaggerParameterImpl parameter = new SwaggerParameterImpl();
		parameter.setDescription((String) content.get("description"));
		parameter.setName((String) content.get("name"));
		parameter.setLocation(ParameterLocation.valueOf(((String) content.get("in")).toUpperCase()));
		if (name == null) {
			name = parameter.getName();
		}
		ComplexContent schema = (ComplexContent) content.get("schema");
		if (schema != null) {
			parameter.setElement(parseSchemaPart(definition, name, schema));
		}
		// Describes how the parameter value will be serialized depending on the type of the parameter value. Default values (based on value of in): for query - form; for path - simple; for header - simple; for cookie - form.
		String style = (String) content.get("style");
		// might be parsed as a boolean or a string, unsure
		Object explodeContent = content.get("explode");
		// When this is true, parameter values of type array or object generate separate parameters for each value of the array or key-value pair of the map. For other types of parameters this property has no effect. When style is form, the default value is true. For all other styles, the default value is false.
		boolean explode = explodeContent != null && 
			((explodeContent instanceof Boolean && (Boolean) explodeContent)
					|| (explodeContent instanceof String && explodeContent.equals("true")));
		Object allowReservedContent = content.get("allowReserved");
		// Determines whether the parameter value SHOULD allow reserved characters, as defined by RFC3986 :/?#[]@!$&'()*+,;= to be included without percent-encoding. This property only applies to parameters with an in value of query. The default value is false.
		boolean allowReserved = allowReservedContent != null && 
				((allowReservedContent instanceof Boolean && (Boolean) allowReservedContent)
						|| (allowReservedContent instanceof String && allowReservedContent.equals("true")));
		
		parameter.setExplode(explode);
		parameter.setAllowReserved(allowReserved);
		// for arrays, the style maps mostly to the collection format options we had before
		if (style != null) {
			// Simple style parameters defined by RFC6570. This option replaces collectionFormat with a csv value from OpenAPI 2.0.
			if (style.equalsIgnoreCase("simple")) {
				parameter.setCollectionFormat(CollectionFormat.CSV);
			}
			// Space separated array values. This option replaces collectionFormat equal to ssv from OpenAPI 2.0.
			else if (style.equalsIgnoreCase("spaceDelimited")) {
				parameter.setCollectionFormat(CollectionFormat.SSV);
			}
			else if (style.equalsIgnoreCase("pipeDelimited")) {
				parameter.setCollectionFormat(CollectionFormat.PIPES);
			}
			// Form style parameters defined by RFC6570. This option replaces collectionFormat with a csv (when explode is false) or multi (when explode is true) value from OpenAPI 2.0.
			else if (style.equalsIgnoreCase("form")) {
				parameter.setCollectionFormat(explode ? CollectionFormat.CSV : CollectionFormat.MULTI);
			}
			else if (style.equalsIgnoreCase("label")) {
				parameter.setCollectionFormat(CollectionFormat.LABEL);
			}
			else if (style.equalsIgnoreCase("matrix")) {
				parameter.setCollectionFormat(explode ? CollectionFormat.MATRIX_IMPLODE : CollectionFormat.MATRIX_EXPLODE);
			}
			else if (style.equalsIgnoreCase("deepObject")) {
				throw new UnsupportedOperationException("Style deepObject is not yet supported");
			}
		}
		if (parameter != null && referencePath != null) {
			references.put(referencePath + "/" + name, parameter);
		}
		return parameter;
	}
	
	private List<SwaggerParameter> parseComponentHeaders(SwaggerDefinitionImpl definition, ComplexContent content, String referencePath) throws ParseException {
		List<SwaggerParameter> headers = new ArrayList<SwaggerParameter>();
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			headers.add(parseComponentHeader(definition, child.getName(), (ComplexContent) content.get(child.getName()), referencePath));
		}
		return headers;
	}
	private SwaggerParameterImpl parseComponentHeader(SwaggerDefinitionImpl definition, String name, ComplexContent content, String referencePath) throws ParseException {
		SwaggerParameterImpl parameter = new SwaggerParameterImpl();
		parameter.setDescription((String) content.get("description"));
		ComplexContent schema = (ComplexContent) content.get("schema");
		if (schema != null) {
			parameter.setElement(parseSchemaPart(definition, name, schema));
		}
		if (parameter != null && referencePath != null) {
			references.put(referencePath + "/" + name, parameter);
		}
		return parameter;
	}
	
	private void parseComponentSecuritySchemes(SwaggerDefinitionImpl definition, ComplexContent content) {
		
	}
	
	private List<SwaggerPath> parsePaths(SwaggerDefinitionImpl definition, ComplexContent content) throws ParseException {
		List<SwaggerPath> paths = new ArrayList<SwaggerPath>();
		for (Element<?> child : TypeUtils.getAllChildren(content.getType())) {
			SwaggerPathImpl path = new SwaggerPathImpl();
			List<SwaggerMethod> methods = new ArrayList<SwaggerMethod>();
			String url = child.getName();
			ComplexContent pathContent = (ComplexContent) content.get(url);
			path.setDescription((String) pathContent.get("description"));
			path.setSummary((String) pathContent.get("summary"));
			if (pathContent.get("$ref") != null) {
				throw new UnsupportedOperationException("Importing entire paths is not yet supported");
			}
			if (pathContent.get("servers") != null) {
				throw new UnsupportedOperationException("Custom servers at the path level are not yet supported");
			}
			Map<String, SwaggerParameter> parameters = new HashMap<String, SwaggerParameter>();
			if (pathContent != null) {
				Object parameterContent = pathContent.get("parameters");
				if (parameterContent instanceof Iterable) {
					for (Object singleParameterContent : (Iterable<?>) parameterContent) {
						SwaggerParameterImpl parameter = parseComponentParameter(definition, null, (ComplexContent) singleParameterContent, null);
						if (parameter != null && parameter.getName() != null) {
							parameters.put(parameter.getName(), parameter);
						}
					}
				}
				List<String> allowedMethods = Arrays.asList("get", "post", "put", "delete", "options", "head", "patch", "trace");
				for (Element<?> methodChild : TypeUtils.getAllChildren(pathContent.getType())) {
					// not a method
					if (!allowedMethods.contains(methodChild.getName().toLowerCase())) {
						continue;
					}
					methods.add(parseMethod(definition, (ComplexContent) pathContent.get(methodChild.getName()), url, methodChild.getName()));
				}
			}
			path.setPath(url);
			path.setMethods(methods);
			path.setParameters(parameters);
			paths.add(path);
		}
		return paths;
	}
	
	private SwaggerMethod parseMethod(SwaggerDefinitionImpl definition, ComplexContent content, String path, String methodName) throws ParseException {
		SwaggerMethodImpl method = new SwaggerMethodImpl();
		method.setTags((List<String>) content.get("tags"));
		method.setSummary((String) content.get("summary"));
		method.setDescription((String) content.get("description"));
		method.setOperationId(SwaggerParser.cleanupOperationId(path, methodName, method.getOperationId()));
		method.setMethod(methodName);
		
		// parameters
		List<SwaggerParameter> parameters = new ArrayList<SwaggerParameter>();
		Object parameterContent = content.get("parameters");
		if (parameterContent instanceof Iterable) {
			for (Object singleParameterContent : (Iterable<?>) parameterContent) {
				SwaggerParameterImpl parameter = parseComponentParameter(definition, null, (ComplexContent) singleParameterContent, null);
				if (parameter != null && parameter.getName() != null) {
					parameters.add(parameter);
				}
			}
		}
		method.setParameters(parameters);
		
		// request body
		ComplexContent requestBody = (ComplexContent) content.get("requestBody");
		SwaggerRequest request = null;
		if (requestBody != null) {
			String ref = (String) requestBody.get("$ref");
			if (ref != null) {
				request = (SwaggerRequest) references.get(ref);
			}
			else {
				request = parseComponentRequestBody(definition, "body", requestBody, null);
			}
		}
		if (request != null) {
			method.setConsumes(request.getConsumes());
			SwaggerParameterImpl body = new SwaggerParameterImpl();
			body.setName("body");
			body.setLocation(ParameterLocation.BODY);
			body.setElement(request.getElement());
			parameters.add(body);
		}
		
		// responses
		List<SwaggerResponse> responses = new ArrayList<SwaggerResponse>();
		ComplexContent responseContent = (ComplexContent) content.get("responses");
		if (responseContent != null) {
			for (Element<?> responseChild : TypeUtils.getAllChildren(responseContent.getType())) {
				ComplexContent responseChildContent = (ComplexContent) responseContent.get(responseChild.getName());
				String ref = (String) responseChildContent.get("$ref");
				SwaggerResponse response;
				if (ref != null) {
					response = (SwaggerResponse) references.get(ref); 
				}
				else {
					response = parseComponentResponse(definition, responseChild.getName(), (ComplexContent) responseContent.get(responseChild.getName()), null);
				}
				responses.add(response);
			}
		}
		method.setResponses(responses);
		
		return method;
	}

	public TimeZone getTimezone() {
		return timezone;
	}

	public void setTimezone(TimeZone timezone) {
		this.timezone = timezone;
	}

	public boolean isAllowUuid() {
		return allowUuid;
	}

	public void setAllowUuid(boolean allowUuid) {
		this.allowUuid = allowUuid;
	}

	public UUIDFormat getUuidFormat() {
		return uuidFormat;
	}

	public void setUuidFormat(UUIDFormat uuidFormat) {
		this.uuidFormat = uuidFormat;
	}
	
}
