package org.wikibrain.spatial.core.dao.postgis;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.data.*;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.visitor.CalcResult;
import org.geotools.feature.visitor.UniqueVisitor;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.jdbc.Index;
import org.geotools.jdbc.JDBCDataStore;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.wikibrain.conf.Configuration;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.spatial.core.SpatialContainerMetadata;

import java.io.IOException;
import java.sql.Connection;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Brent Hecht on 12/30/13.
 * Heavily revised in late March / early April 2014
 */
public class PostGISDB {

    private static final Logger LOG = Logger.getLogger(PostGISDB.class.getName());

    private static final String SPATIAL_DB_NAME = "geometries";
    private static final String GEOM_FIELD_NAME = "geometry";
    private static final String LAYER_FIELD_NAME = "layer_name";
    private static final String REF_SYS_FIELD_NAME = "ref_sys_name";
    private static final String ITEM_ID_FIELD_NAME = "item_id";


    private JDBCDataStore store;

    protected static PostGISDB instance; // singleton instance to avoid having multiple JDBCDataStores, as recommended in GeoTools documentation


//    private Transaction curSaveTransaction;

    public PostGISDB(String dbType, String host, Integer port, String schema, String db, String user, String pwd,
                     int maxConnections) throws DaoException{

        Map<String, Object> params = Maps.newHashMap();
        params.put("dbtype", dbType);
        params.put("host", host);
        params.put("port", port);
        params.put("schema", schema);
        params.put("database", db);
        params.put("user", user);
        params.put("passwd", pwd);
        params.put("max connections", maxConnections);
        initialize(params);

    }

    public Geometry getGeometry(int itemId, String layerName, String refSysName) throws DaoException{

        try {

            FeatureSource contents = getFeatureSource();
            String cqlQuery = String.format("item_id = %d AND layer_name = '%s' AND ref_sys_name = '%s'", itemId, layerName, refSysName);
            Filter f = CQL.toFilter(cqlQuery);
            FeatureCollection collection = contents.getFeatures(f);


            if (collection.size() == 0) return null;

            FeatureIterator iterator = collection.features();

            if(!iterator.hasNext()){
                iterator.close();
                return null;
            }

            Feature feature = iterator.next();
            iterator.close();
            return ((Geometry)feature.getDefaultGeometryProperty().getValue());


        }catch(Exception e){

            throw new DaoException(e);
        }
    }


    public SpatialContainerMetadata getLayerMetadata(String layerName, String refSysName) throws DaoException{

        FeatureIterator iterator = getFeatureIteratorForLayer(layerName, refSysName);
        int count = 0;
        SpatialContainerMetadata.ShapeType shapeType = null;

        while(iterator.hasNext()){
            Feature f = iterator.next();
            count++;
            SpatialContainerMetadata.ShapeType curShapeType = SpatialContainerMetadata.getShapeTypeFromGeometry((Geometry)f.getDefaultGeometryProperty().getValue());
            if (shapeType == null){
                shapeType = curShapeType;
            }else{
                if (!shapeType.equals(SpatialContainerMetadata.ShapeType.MIXED)) {
                    if (!curShapeType.equals(shapeType)) {
                        shapeType = SpatialContainerMetadata.ShapeType.MIXED;
                    }
                }
            }
        }

        return new SpatialContainerMetadata(layerName, refSysName, count, shapeType);

    }

    private FeatureIterator getFeatureIteratorForLayer(String layerName, String refSysName) throws DaoException{

        try{

            FeatureSource contents = getFeatureSource();
            String cqlQuery = String.format("layer_name = '%s' AND ref_sys_name = '%s'", layerName, refSysName);
            Filter f = CQL.toFilter(cqlQuery);
            FeatureCollection collection = contents.getFeatures(f);

            if (collection.size() == 0) return null;

            FeatureIterator iterator = collection.features();
            return iterator;

        }catch(Exception e){
            throw new DaoException(e);
        }

    }

    /**
     * Gets all the layers in a given reference system
     * @param refSysName
     * @return empty set if no layers or refSys does not exist, otherwise a set with all the layers in the input refsys
     * @throws DaoException
     */
    public Set<String> getLayersInReferenceSystem(String refSysName) throws DaoException{

        try {

            String cqlQuery = String.format("ref_sys_name = '%s'", refSysName);
            Filter f = CQL.toFilter(cqlQuery);
            Set<Object> uniques = getUniqueValues("layer_name", f);
            Set<String> rVal = Sets.newHashSet();
            for(Object o : uniques){
                rVal.add(o.toString());
            }
            return rVal;

        }catch(Exception e){
            throw new DaoException(e);
        }

    }


    /**
     * Gets all loaded reference systems
      * @return
     * @throws DaoException
     */
    public Set<String> getAllReferenceSystems() throws DaoException{

        try {

            Set<Object> uniques = getUniqueValues("ref_sys_name", null);
            Set<String> rVal = Sets.newHashSet();
            for(Object o : uniques){
                rVal.add(o.toString());
            }
            return rVal;

        }catch(Exception e){
            throw new DaoException(e);
        }

    }



    /**
     * Returns the unique values in colName in the database, pre-filtered with filter f (if not null)
     * @param colName
     * @param f If null, will not use filter
     * @return
     * @throws DaoException
     */
    private Set<Object> getUniqueValues(String colName, Filter f) throws DaoException{

        try {

            // get feature source
            FeatureSource contents = getFeatureSource();

            // prep query
            Query query = getQuery();
            if (f != null) {
                query.setFilter(f);
            }
            query.setPropertyNames(new String[]{colName});

            UniqueVisitor visitor = new UniqueVisitor(colName);

            // get unique results
            FeatureCollection collection = contents.getFeatures(query);
            collection.accepts(visitor, null);
            CalcResult result = visitor.getResult();
            Set<Object> results = result.toSet();

            return results;

        }catch(Exception e){
            throw new DaoException(e);
        }


    }

    public Map<Integer, Geometry> getAllGeometriesInLayer(String layerName, String refSysName) throws DaoException{

        FeatureIterator iterator = getFeatureIteratorForLayer(layerName, refSysName);

        if(!iterator.hasNext()){
            iterator.close();
            return null;
        }
        Feature feature = iterator.next();

        Map<Integer, Geometry> geometries = new HashMap<Integer, Geometry>();
        while (iterator.hasNext()){
            geometries.put((Integer)((SimpleFeatureImpl)feature).getAttribute("item_id"), (Geometry) feature.getDefaultGeometryProperty().getValue());
            feature = iterator.next();
        }
        iterator.close();
        return geometries;


    }


    private void initialize(Map<String, Object> manualParameters) throws DaoException {

        try {

            store = (JDBCDataStore)DataStoreFinder.getDataStore(manualParameters);



            if (needsToBeInitialized()){ // needs to be initialized

                LOG.log(Level.INFO, "Initializing spatial database tables");

                try {

                    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();

                    builder.setName(SPATIAL_DB_NAME);
                    builder.add(ITEM_ID_FIELD_NAME, Integer.class);
                    builder.add(LAYER_FIELD_NAME, String.class);
                    builder.add(REF_SYS_FIELD_NAME, String.class);
                    builder.add(GEOM_FIELD_NAME, Geometry.class);

                    SimpleFeatureType featureType = builder.buildFeatureType();
                    store.createSchema(featureType);

                    // note: gist index is created automatically above
                    Index regIndex = new Index(SPATIAL_DB_NAME, "rs_layer_type", true, ITEM_ID_FIELD_NAME,LAYER_FIELD_NAME,REF_SYS_FIELD_NAME);

                }catch(Exception e){
                    throw new DaoException(e);
                }

            }else{

                LOG.log(Level.INFO, "Spatial database tables have already been initialized");

            }

        }catch(Exception e){
            throw new DaoException(e);
        }
    }

    public PostGISDB(Map<String, Object> manualParameters) throws DaoException{
        initialize(manualParameters);
    }

    public JDBCDataStore getRawDataStore() {
        return store;
    }

    public Query getQuery(){
        return new Query(SPATIAL_DB_NAME);
    }

    public FeatureSource getFeatureSource() throws DaoException{
        try {
            return store.getFeatureSource(SPATIAL_DB_NAME);
        }catch(IOException e){
            throw new DaoException(e);
        }
    }

    public SimpleFeatureType getSchema() throws DaoException{

        try {
            return store.getSchema(SPATIAL_DB_NAME);
        }catch(IOException e) {
            throw new DaoException(e);
        }
    }

    public String getGeometryAttributeName(){
        return GEOM_FIELD_NAME;
    }

    public String getLayerAttributeName(){return LAYER_FIELD_NAME;}

    public String getRefSysAttributeName() {return REF_SYS_FIELD_NAME;}

    public String getItemIdAttributeName() {return ITEM_ID_FIELD_NAME;}


    private boolean needsToBeInitialized() throws DaoException{

        try {

            // loop through all table names to see if the db name is one of them
            for (String typeName : store.getTypeNames()){
                if (typeName.equals(SPATIAL_DB_NAME)) return false;
            }
            return true;

        }catch(IOException e){
            throw new DaoException(e);
        }

    }



    public static class Provider extends org.wikibrain.conf.Provider<PostGISDB> {
        public Provider(Configurator configurator, Configuration config) throws ConfigurationException {
            super(configurator, config);
        }

        @Override
        public Class getType() {
            return PostGISDB.class;
        }

        @Override
        public String getPath() {
            return "spatial.dao.dataSource";
        }

        @Override
        public PostGISDB get(String name, Config config, Map<String, String> runtimeParams) throws ConfigurationException {

            try {


                if (PostGISDB.instance == null) {

                    // This loads the parameters directly into a map that can be used by JDBCDataStores in Geotools
                    // The format in the conf file must match the the JDBCDataStore format
                    // http://docs.geotools.org/stable/userguide/library/jdbc/datastore.html

                    Map<String, Object> params = Maps.newHashMap();
                    ConfigObject cObject = config.root();
                    for (String key : cObject.keySet()) {
                        params.put(key, cObject.get(key).unwrapped());
                    }

                    PostGISDB.instance = new PostGISDB(params);
                }

                return PostGISDB.instance;

            } catch (DaoException e) {
                throw new ConfigurationException(e);
            }
        }
    }


}
