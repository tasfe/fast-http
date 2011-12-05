package org.neo4j.smack.api;

import org.neo4j.server.rest.paging.LeaseManager;
import org.neo4j.server.rest.paging.RealClock;
import org.neo4j.server.rest.repr.*;
import org.neo4j.server.rest.web.DatabaseActions;
import org.neo4j.server.rest.web.PropertyValueException;
import org.neo4j.server.rest.web.RestfulGraphDatabase;
import org.neo4j.server.smack.serialization.RepresentationSerializationStrategy;
import org.neo4j.smack.annotation.DeserializeWith;
import org.neo4j.smack.annotation.SerializeWith;
import org.neo4j.smack.annotation.Transactional;
import org.neo4j.smack.event.Invocation;
import org.neo4j.smack.event.Result;
import org.neo4j.smack.serialization.strategy.PropertyMapDeserializationStrategy;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static java.util.Arrays.asList;

/**
 * @author mh
 * @since 05.12.11
 */
public class RestDatabaseService
{
    @SuppressWarnings( "serial" )
    public static class AmpersandSeparatedCollection extends LinkedHashSet<String>
    {
        public AmpersandSeparatedCollection( String path )
        {
            for ( String e : path.split( "&" ) )
            {
                if ( e.trim()
                        .length() > 0 )
                {
                    add( e );
                }
            }
        }
    }

    private static final String PATH_NODES = "node";
    private static final String PATH_NODE = PATH_NODES + "/{nodeId}";
    private static final String PATH_NODE_PROPERTIES = PATH_NODE + "/properties";
    private static final String PATH_NODE_PROPERTY = PATH_NODE_PROPERTIES + "/{key}";
    private static final String PATH_NODE_RELATIONSHIPS = PATH_NODE + "/relationships";
    private static final String PATH_RELATIONSHIP = "relationship/{relationshipId}";
    private static final String PATH_NODE_RELATIONSHIPS_W_DIR = PATH_NODE_RELATIONSHIPS + "/{direction}";
    private static final String PATH_NODE_RELATIONSHIPS_W_DIR_N_TYPES = PATH_NODE_RELATIONSHIPS_W_DIR + "/{types}";
    private static final String PATH_RELATIONSHIP_PROPERTIES = PATH_RELATIONSHIP + "/properties";
    private static final String PATH_RELATIONSHIP_PROPERTY = PATH_RELATIONSHIP_PROPERTIES + "/{key}";
    private static final String PATH_NODE_TRAVERSE = PATH_NODE + "/traverse/{returnType}";
    private static final String PATH_NODE_PATH = PATH_NODE + "/path";
    private static final String PATH_NODE_PATHS = PATH_NODE + "/paths";

    protected static final String PATH_NODE_INDEX = "index/node";
    protected static final String PATH_NAMED_NODE_INDEX = PATH_NODE_INDEX + "/{indexName}";
    protected static final String PATH_NODE_INDEX_GET = PATH_NAMED_NODE_INDEX + "/{key}/{value}";
    protected static final String PATH_NODE_INDEX_QUERY_WITH_KEY = PATH_NAMED_NODE_INDEX + "/{key}"; // http://localhost/db/data/index/node/foo?query=somelucenestuff
    protected static final String PATH_NODE_INDEX_ID = PATH_NODE_INDEX_GET + "/{id}";
    protected static final String PATH_NODE_INDEX_REMOVE_KEY = PATH_NAMED_NODE_INDEX + "/{key}/{id}";
    protected static final String PATH_NODE_INDEX_REMOVE = PATH_NAMED_NODE_INDEX + "/{id}";

    protected static final String PATH_RELATIONSHIP_INDEX = "index/relationship";
    protected static final String PATH_NAMED_RELATIONSHIP_INDEX = PATH_RELATIONSHIP_INDEX + "/{indexName}";
    protected static final String PATH_RELATIONSHIP_INDEX_GET = PATH_NAMED_RELATIONSHIP_INDEX + "/{key}/{value}";
    protected static final String PATH_RELATIONSHIP_INDEX_QUERY_WITH_KEY = PATH_NAMED_RELATIONSHIP_INDEX + "/{key}";
    protected static final String PATH_RELATIONSHIP_INDEX_ID = PATH_RELATIONSHIP_INDEX_GET + "/{id}";
    protected static final String PATH_RELATIONSHIP_INDEX_REMOVE_KEY = PATH_NAMED_RELATIONSHIP_INDEX + "/{key}/{id}";
    protected static final String PATH_RELATIONSHIP_INDEX_REMOVE = PATH_NAMED_RELATIONSHIP_INDEX + "/{id}";

    public static final String PATH_AUTO_NODE_INDEX = "index/auto/node";
    protected static final String PATH_AUTO_NODE_INDEX_GET = PATH_AUTO_NODE_INDEX + "/{key}/{value}";

    public static final String PATH_AUTO_RELATIONSHIP_INDEX = "index/auto/relationship";
    protected static final String PATH_AUTO_RELATIONSHIP_INDEX_GET = PATH_AUTO_RELATIONSHIP_INDEX + "/{key}/{value}";

    private static final String SIXTY_SECONDS = "60";
    private static final String FIFTY = "50";

    public static final String PATH_TO_CREATE_PAGED_TRAVERSERS = PATH_NODE + "/paged/traverse/{returnType}";
    public static final String PATH_TO_PAGED_TRAVERSERS = PATH_NODE + "/paged/traverse/{returnType}/{traverserId}";

    public RestDatabaseService()
    {
    }

    private static final LeaseManager leaseManager = new LeaseManager( new RealClock() );


    private RestfulGraphDatabase createRestGraphDatabase(Invocation invocation) {
        URI baseUri=null;
        final UriInfo uriInfo = null;

        final RepresentationFormatRepository repository = new RepresentationFormatRepository(null);
        final InputFormat jsonInputFormat = repository.inputFormat(MediaType.APPLICATION_JSON_TYPE);
        final OutputFormat jsonOutputFormat = repository.outputFormat(asList(MediaType.APPLICATION_JSON_TYPE), baseUri);
        return new RestfulGraphDatabase(uriInfo,new org.neo4j.server.database.Database(invocation.getDatabase().getGraphDB()),jsonInputFormat, jsonOutputFormat,leaseManager);
    }

    private DatabaseActions createDatabaseActions(Invocation invocation) {
        return new DatabaseActions(new org.neo4j.server.database.Database(invocation.getDatabase().getGraphDB()),leaseManager);
    }

    private static Response nothing()
    {
        return Response.noContent()
                .build();
    }

    private long extractNodeId( String uri ) throws BadInputException
    {
        try
        {
            return Long.parseLong( uri.substring( uri.lastIndexOf( "/" ) + 1 ) );
        }
        catch ( NumberFormatException ex )
        {
            throw new BadInputException( ex );
        }
        catch ( NullPointerException ex )
        {
            throw new BadInputException( ex );
        }
    }

    private long extractRelationshipId(String uri) throws BadInputException
    {
        return extractNodeId( uri );
    }

    RepresentationFormatRepository repository = new RepresentationFormatRepository(new ExtensionInjector() {
        @Override
        public Map<String, List<String>> getExensionsFor(Class<?> aClass) {
            return Collections.emptyMap();
        }
    });

    private OutputFormat createOutputFormat(Invocation invocation) throws URISyntaxException {
        final URI uri = new URI("/db/data"); // TODO invocation.getPath() is full path of route, need invocation.getBaseUri() from router
        return repository.outputFormat(Arrays.asList(MediaType.APPLICATION_JSON_TYPE), uri);
    }


    @GET
    @SerializeWith(RepresentationSerializationStrategy.class)
    public void getRoot(Invocation invocation, Result result)
    {
         result.setOk(createDatabaseActions(invocation).root());
    }

    @POST
    @DeserializeWith(PropertyMapDeserializationStrategy.class)
    @SerializeWith(RepresentationSerializationStrategy.class)
    @Transactional
    @Path( PATH_NODES )
    public void createNode( Invocation invocation, Result result ) throws PropertyValueException, URISyntaxException {
        final NodeRepresentation node = createDatabaseActions(invocation).createNode((Map<String, Object>) invocation.getDeserializedContent());
        final String location = createOutputFormat(invocation).format(node.selfUri());
        result.setCreated(location);
        result.setData(node);
    }

    // Nodes
/*
    @POST
    @Path( PATH_NODES )
    public Response createNode( Invocation invocation, Result result )
    {
        try
        {
            return output.created( actions.createNode( input.readMap( body ) ) );
        }
        catch ( ArrayStoreException ase )
        {
            return generateBadRequestDueToMangledJsonResponse( body );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
    }

    private Response generateBadRequestDueToMangledJsonResponse( String body )
    {
        return Response.status( 400 )
                .type( MediaType.TEXT_PLAIN )
                .entity( "Invalid JSON array in POST body: " + body )
                .build();
    }

    @GET
    @Path( PATH_NODE )
    public Response getNode( @PathParam( "nodeId" ) long nodeId )
    {
        try
        {
            return output.ok( actions.getNode( nodeId ) );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
    }

    @DELETE
    @Path( PATH_NODE )
    public Response deleteNode( @PathParam( "nodeId" ) long nodeId )
    {
        try
        {
            actions.deleteNode( nodeId );
            return nothing();
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
        catch ( OperationFailureException e )
        {
            return output.conflict( e );
        }
    }

    // Node properties

    @PUT
    @Path( PATH_NODE_PROPERTIES )
    public Response setAllNodeProperties( @PathParam( "nodeId" ) long nodeId, String body )
    {
        try
        {
            actions.setAllNodeProperties( nodeId, input.readMap( body ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( ArrayStoreException ase )
        {
            return generateBadRequestDueToMangledJsonResponse( body );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    @GET
    @Path( PATH_NODE_PROPERTIES )
    public Response getAllNodeProperties( @PathParam( "nodeId" ) long nodeId )
    {
        final PropertiesRepresentation properties;
        try
        {
            properties = actions.getAllNodeProperties( nodeId );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }

        if ( properties.isEmpty() )
        {
            return nothing();
        }

        return output.ok( properties );
    }

    @PUT
    @Path( PATH_NODE_PROPERTY )
    public Response setNodeProperty( @PathParam( "nodeId" ) long nodeId, @PathParam( "key" ) String key, String body )
    {
        try
        {
            actions.setNodeProperty( nodeId, key, input.readValue( body ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( ArrayStoreException ase )
        {
            return generateBadRequestDueToMangledJsonResponse( body );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    @GET
    @Path( PATH_NODE_PROPERTY )
    public Response getNodeProperty( @PathParam( "nodeId" ) long nodeId, @PathParam( "key" ) String key )
    {
        try
        {
            return output.ok( actions.getNodeProperty( nodeId, key ) );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
        catch ( NoSuchPropertyException e )
        {
            return output.notFound( e );
        }
    }

    @DELETE
    @Path( PATH_NODE_PROPERTY )
    public Response deleteNodeProperty( @PathParam( "nodeId" ) long nodeId, @PathParam( "key" ) String key )
    {
        try
        {
            actions.removeNodeProperty( nodeId, key );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
        catch ( NoSuchPropertyException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    @DELETE
    @Path( PATH_NODE_PROPERTIES )
    public Response deleteAllNodeProperties( @PathParam( "nodeId" ) long nodeId )
    {
        try
        {
            actions.removeAllNodeProperties( nodeId );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    // Relationships

    @SuppressWarnings( "unchecked" )
    @POST
    @Path( PATH_NODE_RELATIONSHIPS )
    public Response createRelationship( @PathParam( "nodeId" ) long startNodeId, String body )
    {
        final Map<String, Object> data;
        final long endNodeId;
        final String type;
        final Map<String, Object> properties;
        try
        {
            data = input.readMap( body );
            endNodeId = extractNodeId( (String) data.get( "to" ) );
            type = (String) data.get( "type" );
            properties = (Map<String, Object>) data.get( "data" );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( ClassCastException e )
        {
            return output.badRequest( e );
        }
        try
        {
            return output.created( actions.createRelationship( startNodeId, endNodeId, type, properties ) );
        }
        catch ( StartNodeNotFoundException e )
        {
            return output.notFound( e );
        }
        catch ( EndNodeNotFoundException e )
        {
            return output.badRequest( e );
        }
        catch ( PropertyValueException e )
        {
            return output.badRequest( e );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
    }

    @GET
    @Path( PATH_RELATIONSHIP )
    public Response getRelationship( @PathParam( "relationshipId" ) long relationshipId )
    {
        try
        {
            return output.ok( actions.getRelationship( relationshipId ) );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
    }

    @DELETE
    @Path( PATH_RELATIONSHIP )
    public Response deleteRelationship( @PathParam( "relationshipId" ) long relationshipId )
    {
        try
        {
            actions.deleteRelationship( relationshipId );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    @GET
    @Path( PATH_NODE_RELATIONSHIPS_W_DIR )
    public Response getNodeRelationships( @PathParam( "nodeId" ) long nodeId,
            @PathParam( "direction" ) RelationshipDirection direction )
    {
        try
        {
            return output.ok( actions.getNodeRelationships( nodeId, direction, Collections.<String>emptyList() ) );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
    }

    @GET
    @Path( PATH_NODE_RELATIONSHIPS_W_DIR_N_TYPES )
    public Response getNodeRelationships( @PathParam( "nodeId" ) long nodeId,
            @PathParam( "direction" ) RelationshipDirection direction,
            @PathParam( "types" ) AmpersandSeparatedCollection types )
    {
        try
        {
            return output.ok( actions.getNodeRelationships( nodeId, direction, types ) );
        }
        catch ( NodeNotFoundException e )
        {
            return output.notFound( e );
        }
    }

    // Relationship properties

    @GET
    @Path( PATH_RELATIONSHIP_PROPERTIES )
    public Response getAllRelationshipProperties( @PathParam( "relationshipId" ) long relationshipId )
    {
        final PropertiesRepresentation properties;
        try
        {
            properties = actions.getAllRelationshipProperties( relationshipId );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
        if ( properties.isEmpty() )
        {
            return nothing();
        }
        else
        {
            return output.ok( properties );
        }
    }

    @GET
    @Path( PATH_RELATIONSHIP_PROPERTY )
    public Response getRelationshipProperty( @PathParam( "relationshipId" ) long relationshipId,
            @PathParam( "key" ) String key )
    {
        try
        {
            return output.ok( actions.getRelationshipProperty( relationshipId, key ) );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
        catch ( NoSuchPropertyException e )
        {
            return output.notFound( e );
        }
    }

    @PUT
    @Path( PATH_RELATIONSHIP_PROPERTIES )
    @Consumes( MediaType.APPLICATION_JSON )
    public Response setAllRelationshipProperties( @PathParam( "relationshipId" ) long relationshipId, String body )
    {
        try
        {
            actions.setAllRelationshipProperties( relationshipId, input.readMap( body ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    @PUT
    @Path( PATH_RELATIONSHIP_PROPERTY )
    @Consumes( MediaType.APPLICATION_JSON )
    public Response setRelationshipProperty( @PathParam( "relationshipId" ) long relationshipId,
            @PathParam( "key" ) String key, String body )
    {
        try
        {
            actions.setRelationshipProperty( relationshipId, key, input.readValue( body ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    @DELETE
    @Path( PATH_RELATIONSHIP_PROPERTIES )
    public Response deleteAllRelationshipProperties( @PathParam( "relationshipId" ) long relationshipId )
    {
        try
        {
            actions.removeAllRelationshipProperties( relationshipId );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    @DELETE
    @Path( PATH_RELATIONSHIP_PROPERTY )
    public Response deleteRelationshipProperty( @PathParam( "relationshipId" ) long relationshipId,
            @PathParam( "key" ) String key )
    {
        try
        {
            actions.removeRelationshipProperty( relationshipId, key );
        }
        catch ( RelationshipNotFoundException e )
        {
            return output.notFound( e );
        }
        catch ( NoSuchPropertyException e )
        {
            return output.notFound( e );
        }
        return nothing();
    }

    // Index

    @GET
    @Path( PATH_NODE_INDEX )
    public Response getNodeIndexRoot()
    {
        if ( actions.getNodeIndexNames().length == 0 )
        {
            return output.noContent();
        }
        return output.ok( actions.nodeIndexRoot() );
    }

    @POST
    @Path( PATH_NODE_INDEX )
    @Consumes( MediaType.APPLICATION_JSON )
    public Response jsonCreateNodeIndex( String json )
    {
        try
        {
            return output.created( actions.createNodeIndex( input.readMap( json ) ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
    }

    @GET
    @Path( PATH_RELATIONSHIP_INDEX )
    public Response getRelationshipIndexRoot()
    {
        if ( actions.getRelationshipIndexNames().length == 0 )
        {
            return output.noContent();
        }
        return output.ok( actions.relationshipIndexRoot() );
    }

    @POST
    @Path( PATH_RELATIONSHIP_INDEX )
    @Consumes( MediaType.APPLICATION_JSON )
    public Response jsonCreateRelationshipIndex( String json )
    {
        try
        {
            return output.created( actions.createRelationshipIndex( input.readMap( json ) ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_NAMED_NODE_INDEX )
    public Response getIndexedNodesByQuery( @PathParam( "indexName" ) String indexName,
            @QueryParam( "query" ) String query )
    {
        try
        {
            return output.ok( actions.getIndexedNodesByQuery( indexName, query ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_AUTO_NODE_INDEX )
    public Response getAutoIndexedNodesByQuery( @QueryParam( "query" ) String query )
    {
        try
        {
            return output.ok( actions.getAutoIndexedNodesByQuery( query ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @DELETE
    @Path( PATH_NAMED_NODE_INDEX )
    @Consumes( MediaType.APPLICATION_JSON )
    public Response deleteNodeIndex( @PathParam( "indexName" ) String indexName )
    {
        try
        {
            actions.removeNodeIndex( indexName );
            return output.noContent();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
    }

    @DELETE
    @Path( PATH_NAMED_RELATIONSHIP_INDEX )
    @Consumes( MediaType.APPLICATION_JSON )
    public Response deleteRelationshipIndex( @PathParam( "indexName" ) String indexName )
    {
        try
        {
            actions.removeRelationshipIndex( indexName );
            return output.noContent();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
    }

    @POST
    @Path( PATH_NAMED_NODE_INDEX )
    @Consumes( MediaType.APPLICATION_JSON )
    public Response addToNodeIndex( @PathParam( "indexName" ) String indexName, String postBody )
    {

        try
        {
            Map<String, Object> entityBody = input.readMap( postBody );
            return output.created( actions.addToNodeIndex( indexName, String.valueOf( entityBody.get( "key" ) ),
                    String.valueOf( entityBody.get( "value" ) ), extractNodeId( entityBody.get( "uri" ).toString() ) ) );
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }

    }

    @POST
    @Path( PATH_NAMED_RELATIONSHIP_INDEX )
    public Response addToRelationshipIndex( @PathParam( "indexName" ) String indexName, String postBody )
    {
        try
        {
            Map<String, Object> entityBody = input.readMap( postBody );
            return output.created( actions.addToRelationshipIndex( indexName, String.valueOf( entityBody.get( "key" ) ),
                    String.valueOf( entityBody.get( "value" ) ), extractRelationshipId( entityBody.get( "uri" ).toString() ) ) );
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_NODE_INDEX_ID )
    public Response getNodeFromIndexUri( @PathParam( "indexName" ) String indexName, @PathParam( "key" ) String key,
            @PathParam( "value" ) String value, @PathParam( "id" ) long id )
    {
        try
        {
            return output.ok( actions.getIndexedNode( indexName, key, value, id ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_RELATIONSHIP_INDEX_ID )
    public Response getRelationshipFromIndexUri( @PathParam( "indexName" ) String indexName,
            @PathParam( "key" ) String key, @PathParam( "value" ) String value, @PathParam( "id" ) long id )
    {
        return output.ok( actions.getIndexedRelationship( indexName, key, value, id ) );
    }

    @GET
    @Path( PATH_NODE_INDEX_GET )
    public Response getIndexedNodes( @PathParam( "indexName" ) String indexName, @PathParam( "key" ) String key,
            @PathParam( "value" ) String value )
    {
        try
        {
            return output.ok( actions.getIndexedNodes( indexName, key, value ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_AUTO_NODE_INDEX_GET )
    public Response getIndexedNodes( @PathParam( "key" ) String key, @PathParam( "value" ) String value )
    {
        try
        {
            return output.ok( actions.getAutoIndexedNodes( key, value ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_NODE_INDEX_QUERY_WITH_KEY )
    public Response getIndexedNodesByQuery( @PathParam( "indexName" ) String indexName, @PathParam( "key" ) String key,
            @QueryParam( "query" ) String query )
    {
        try
        {
            return output.ok( actions.getIndexedNodesByQuery( indexName, key, query ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_RELATIONSHIP_INDEX_GET )
    public Response getIndexedRelationships( @PathParam( "indexName" ) String indexName,
            @PathParam( "key" ) String key, @PathParam( "value" ) String value )
    {
        try
        {
            return output.ok( actions.getIndexedRelationships( indexName, key, value ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_AUTO_RELATIONSHIP_INDEX_GET )
    public Response getIndexedRelationships( @PathParam( "key" ) String key, @PathParam( "value" ) String value )
    {
        try
        {
            return output.ok( actions.getAutoIndexedRelationships( key, value ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_AUTO_RELATIONSHIP_INDEX )
    public Response getAutoIndexedRelationshipsByQuery( @QueryParam( "query" ) String query )
    {
        try
        {
            return output.ok( actions.getAutoIndexedRelationshipsByQuery( query ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_NAMED_RELATIONSHIP_INDEX )
    public Response getIndexedRelationshipsByQuery( @PathParam( "indexName" ) String indexName,
            @QueryParam( "query" ) String query )
    {
        try
        {
            return output.ok( actions.getIndexedRelationshipsByQuery( indexName, query ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @GET
    @Path( PATH_RELATIONSHIP_INDEX_QUERY_WITH_KEY )
    public Response getIndexedRelationshipsByQuery( @PathParam( "indexName" ) String indexName,
            @PathParam( "key" ) String key, @QueryParam( "query" ) String query )
    {
        try
        {
            return output.ok( actions.getIndexedRelationshipsByQuery( indexName, key, query ) );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @DELETE
    @Path( PATH_NODE_INDEX_ID )
    public Response deleteFromNodeIndex( @PathParam( "indexName" ) String indexName, @PathParam( "key" ) String key,
            @PathParam( "value" ) String value, @PathParam( "id" ) long id )
    {
        try
        {
            actions.removeFromNodeIndex( indexName, key, value, id );
            return nothing();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @DELETE
    @Path( PATH_NODE_INDEX_REMOVE_KEY )
    public Response deleteFromNodeIndexNoValue( @PathParam( "indexName" ) String indexName,
            @PathParam( "key" ) String key, @PathParam( "id" ) long id )
    {
        try
        {
            actions.removeFromNodeIndexNoValue( indexName, key, id );
            return nothing();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @DELETE
    @Path( PATH_NODE_INDEX_REMOVE )
    public Response deleteFromNodeIndexNoKeyValue( @PathParam( "indexName" ) String indexName,
            @PathParam( "id" ) long id )
    {
        try
        {
            actions.removeFromNodeIndexNoKeyValue( indexName, id );
            return nothing();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @DELETE
    @Path( PATH_RELATIONSHIP_INDEX_ID )
    public Response deleteFromRelationshipIndex( @PathParam( "indexName" ) String indexName,
            @PathParam( "key" ) String key, @PathParam( "value" ) String value, @PathParam( "id" ) long id )
    {
        try
        {
            actions.removeFromRelationshipIndex( indexName, key, value, id );
            return nothing();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @DELETE
    @Path( PATH_RELATIONSHIP_INDEX_REMOVE_KEY )
    public Response deleteFromRelationshipIndexNoValue( @PathParam( "indexName" ) String indexName,
            @PathParam( "key" ) String key, @PathParam( "id" ) long id )
    {
        try
        {
            actions.removeFromRelationshipIndexNoValue( indexName, key, id );
            return nothing();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    @DELETE
    @Path( PATH_RELATIONSHIP_INDEX_REMOVE )
    public Response deleteFromRelationshipIndex( @PathParam( "indexName" ) String indexName,
            @PathParam( "value" ) String value, @PathParam( "id" ) long id )
    {
        try
        {
            actions.removeFromRelationshipIndexNoKeyValue( indexName, id );
            return nothing();
        }
        catch ( UnsupportedOperationException e )
        {
            return output.methodNotAllowed( e );
        }
        catch ( NotFoundException nfe )
        {
            return output.notFound( nfe );
        }
        catch ( Exception e )
        {
            return output.serverError( e );
        }
    }

    // Traversal

    @POST
    @Path( PATH_NODE_TRAVERSE )
    public Response traverse( @PathParam( "nodeId" ) long startNode,
            @PathParam( "returnType" ) TraverserReturnType returnType, String body )
    {
        try
        {
            return output.ok( actions.traverse( startNode, input.readMap( body ), returnType ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( NotFoundException e )
        {
            return output.notFound( e );
        }
    }

    // Paged traversal

    @DELETE
    @Path( PATH_TO_PAGED_TRAVERSERS )
    public Response removePagedTraverser( @PathParam( "traverserId" ) String traverserId )
    {

        if ( actions.removePagedTraverse( traverserId ) )
        {
            return Response.ok()
                    .build();
        }
        else
        {
            return output.notFound();
        }

    }

    @GET
    @Path( PATH_TO_PAGED_TRAVERSERS )
    public Response pagedTraverse( @PathParam( "traverserId" ) String traverserId,
            @PathParam( "returnType" ) TraverserReturnType returnType )
    {
        try
        {
            ListRepresentation result = actions.pagedTraverse( traverserId, returnType );

            return Response.ok( uriInfo.getRequestUri() )
                    .entity( output.format( result ) )
                    .build();
        }
        catch ( NotFoundException e )
        {
            return output.notFound( e );
        }
    }

    @POST
    @Path( PATH_TO_CREATE_PAGED_TRAVERSERS )
    public Response createPagedTraverser( @PathParam( "nodeId" ) long startNode,
            @PathParam( "returnType" ) TraverserReturnType returnType,
            @QueryParam( "pageSize" ) @DefaultValue( FIFTY ) int pageSize,
            @QueryParam( "leaseTime" ) @DefaultValue( SIXTY_SECONDS ) int leaseTimeInSeconds, String body )
    {
        try
        {
            validatePageSize( pageSize );
            validateLeaseTime( leaseTimeInSeconds );

            String traverserId = actions.createPagedTraverser( startNode, input.readMap( body ), pageSize,
                    leaseTimeInSeconds );

            String responseBody = output.format( actions.pagedTraverse( traverserId, returnType ) );

            URI uri = new URI( uriInfo.getBaseUri()
                    .toString() + "node/" + startNode + "/paged/traverse/" + returnType + "/" + traverserId );

            return Response.created( uri.normalize() )
                    .entity( responseBody )
                    .build();
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( NotFoundException e )
        {
            return output.notFound( e );
        }
        catch ( URISyntaxException e )
        {
            return output.serverError( e );
        }
    }

    private void validateLeaseTime( int leaseTimeInSeconds ) throws BadInputException
    {
        if ( leaseTimeInSeconds < 1 )
        {
            throw new BadInputException( "Lease time less than 1 second is not supported" );
        }
    }

    private void validatePageSize( int pageSize ) throws BadInputException
    {
        if ( pageSize < 1 )
        {
            throw new BadInputException( "Page size less than 1 is not permitted" );
        }
    }

    @POST
    @Path( PATH_NODE_PATH )
    public Response singlePath( @PathParam( "nodeId" ) long startNode, String body )
    {
        final Map<String, Object> description;
        final long endNode;
        try
        {
            description = input.readMap( body );
            endNode = extractNodeId( (String) description.get( "to" ) );
            return output.ok( actions.findSinglePath( startNode, endNode, description ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( ClassCastException e )
        {
            return output.badRequest( e );
        }
        catch ( NotFoundException e )
        {
            return output.notFound( e );
        }

    }

    @POST
    @Path( PATH_NODE_PATHS )
    public Response allPaths( @PathParam( "nodeId" ) long startNode, String body )
    {
        final Map<String, Object> description;
        final long endNode;
        try
        {
            description = input.readMap( body );
            endNode = extractNodeId( (String) description.get( "to" ) );
            return output.ok( actions.findPaths( startNode, endNode, description ) );
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( ClassCastException e )
        {
            return output.badRequest( e );
        }
    }
    */
}