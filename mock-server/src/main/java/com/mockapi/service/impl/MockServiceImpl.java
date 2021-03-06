package com.mockapi.service.impl;

import com.mockapi.dto.MockResponseDTO;
import com.mockapi.dto.RequestDTO;
import com.mockapi.entity.MockRequest;
import com.mockapi.entity.MockResponse;
import com.mockapi.repository.NoSQLRequestRepository;
import com.mockapi.repository.NoSQLResponseRepository;
import com.mockapi.service.MockService;
import com.mockapi.utils.ServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Service
public class MockServiceImpl implements MockService {

    public static final Logger LOGGER = LoggerFactory.getLogger(MockServiceImpl.class);

    @Autowired
    private NoSQLResponseRepository responseRepository;

    @Autowired
    private NoSQLRequestRepository requestRepository;

    @Autowired
    private Environment environment;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ServiceUtils serviceUtils;

    public MockServiceImpl(ServiceUtils serviceUtils) {
        this.serviceUtils = serviceUtils;
    }

    public ResponseEntity<Object> testMockResponse(String mockId) {
        MockResponse entity = responseRepository.getByMockId(mockId).block();
        if (entity != null) {
            LOGGER.debug("Test Entity from DB : {}",entity);
            LOGGER.debug("Got entity {} for mockID {} ", entity, mockId);
            MultiValueMap<String, String> headers = serviceUtils.getHeadersFromEntity(entity);
            HttpStatus httpStatus = HttpStatus.valueOf(entity.getStatusCode());
            LOGGER.debug("Entering Delay period of {}", entity.getDelay());
            if (entity.getDelay() > 0){
                try {
                    Thread.sleep(entity.getDelay());
                } catch (InterruptedException e) {
                    LOGGER.error("Thread Interrupted {}", Thread.currentThread());
                    Thread.currentThread().interrupt();
                }
            }
            return new ResponseEntity<>(entity.getBody(), headers, httpStatus);
        } else {
            LOGGER.error("No mock Response found for {}", mockId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    public ResponseEntity<Object> saveMockResponse(MockResponseDTO request) {
        MockResponse entity = serviceUtils.getResponseEntityFromDTO(request);

        LOGGER.debug("Saving entity {}", entity);
        responseRepository.save(entity).block();

        String url = null;
        try {
            url = "http://" + InetAddress.getLocalHost().getHostName() + ":" + environment.getProperty("server.port") +
                    "/api/mock/" +
                    entity.getMockId();
        } catch (UnknownHostException e) {
            LOGGER.error("Unable to resolve current hostname ");
            e.printStackTrace();
        }

        return new ResponseEntity<>(url, HttpStatus.OK);
    }

    public ResponseEntity<Object> deleteMockResponse(MockResponseDTO request) {
        try {
            MockResponse entity = responseRepository.getByMockId(request.getMockId()).block();
            assert entity != null;
            LOGGER.debug("Got entity to be deleted: {}", entity);
            LOGGER.info("Deleting {}",entity.getMockId());
            responseRepository.delete(entity).block();
            return new ResponseEntity<>(
                    "{\"message\":\"Deletion Successful \" }", HttpStatus.OK);
        } catch (Exception e){
            LOGGER.error("Error occured while deleting: {}", request.getMockId());
            return new ResponseEntity<>(
                    "{\"message\":\"Failed to delete" +request.getMockId()+" \" }", HttpStatus.NO_CONTENT);
        }
    }

    public ResponseEntity<Object> testEndpoint(MockRequest request) {
        URI uri = serviceUtils.generateUri(request.getHostName(), request.getEndpoint(), request.getSchema());
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        request.getHeaders().forEach(headerDTO -> headers.add(headerDTO.getName(),headerDTO.getValue()));
        RequestEntity<Object> requestEntity;
        requestEntity = new RequestEntity<>(request.getBody(),headers, HttpMethod.resolve(request.getHttpMethod()),uri);
        return restTemplate.exchange(requestEntity, Object.class);
    }

    public ResponseEntity<Object> testDTOEndpoint(RequestDTO requestDTO){
        MockRequest mockRequestFromDTO = serviceUtils.getMockRequestFromDTO(requestDTO);
        return testEndpoint(mockRequestFromDTO);
    }

    public ResponseEntity<Object> saveMockRequest(RequestDTO requestDTO){
        MockRequest entity = serviceUtils.getMockRequestFromDTO(requestDTO);
        LOGGER.debug("Saving entity {}", entity);
        requestRepository.save(entity).block();

        String url = null;
        try {
            url = "http://" + InetAddress.getLocalHost().getHostName() + ":" + environment.getProperty("server.port") +
                    "/api/mock/" +
                    entity.getMockID();
        } catch (UnknownHostException e) {
            LOGGER.error("Unable to resolve current hostname ");
            e.printStackTrace();
        }
        return new ResponseEntity<>(url, HttpStatus.OK);
    }

    public ResponseEntity<Object> deleteMockRequest(RequestDTO request) {
        try {
            MockRequest entity = requestRepository.getByMockID(request.getMockID()).block();
            assert entity != null;
            LOGGER.debug("Got entity to be deleted: {}", entity);
            LOGGER.info("Deleting {}",entity.getMockID());
            requestRepository.delete(entity).block();
            return new ResponseEntity<>(
                    "{\"message\":\"Deletion Successful \" }", HttpStatus.OK);
        } catch (Exception e){
            LOGGER.error("Error occured while deleting: {}", request.getMockID());
            return new ResponseEntity<>(
                    "{\"message\":\"Failed to delete" +request.getMockID()+" \" }", HttpStatus.NO_CONTENT);
        }
    }

    public ResponseEntity<Object> testRequestMockId(String mockId){
        MockRequest entity = requestRepository.getByMockID(mockId).block();
        assert entity != null;
        return testEndpoint(entity);
    }
}
