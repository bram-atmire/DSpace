/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.MaxMind;
import com.maxmind.geoip2.record.Postal;
import com.maxmind.geoip2.record.RepresentedCountry;
import com.maxmind.geoip2.record.Traits;
import org.springframework.beans.factory.InitializingBean;

/**
 * Mock service that uses an embedded SOLR server for the statistics core.
 * <p>
 * <strong>NOTE:</strong>  this class is overridden by one <em>of the same name</em>
 * defined in dspace-server-webapp and declared as a bean there.
 * See {@code test/data/dspaceFolder/config/spring/api/solr-services.xml}.  Some kind of classpath
 * magic makes this work.
 */
public class MockSolrLoggerServiceImpl
        extends SolrLoggerServiceImpl
        implements InitializingBean {

    public MockSolrLoggerServiceImpl() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //We don't use SOLR in the tests of this module
        solr = null;

        // Mock GeoIP's DatabaseReader
        DatabaseReader reader = mock(DatabaseReader.class);
        // Ensure that any tests requesting a city() get a mock/fake CityResponse
        when(reader.city(any(InetAddress.class))).thenReturn(mockCityResponse());
        // Save this mock DatabaseReader to be used by SolrLoggerService
        locationService = reader;
    }

    /**
     * A mock/fake GeoIP CityResponse, which will be used for *all* test statistical requests
     * @return faked CityResponse
     */
    private CityResponse mockCityResponse() {
        List<String> cityNames = new ArrayList<String>(Collections.singleton("New York"));
        City city = new City(cityNames, 1, 1, new HashMap());

        List<String> countryNames = new ArrayList<String>(Collections.singleton("United States"));
        Country country = new Country(countryNames, 1, 1, "US", new HashMap());

        Location location = new Location(1, 1, 40.760498D, -73.9933D, 501, 1, "EST");

        Postal postal = new Postal("10036", 1);

        return new CityResponse(city, new Continent(), country, location, new MaxMind(), postal,
                                country, new RepresentedCountry(), new ArrayList<>(0),
                                new Traits());
    }
}
