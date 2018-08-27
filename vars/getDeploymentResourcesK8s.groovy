#!/usr/bin/groovy

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
    body()

    def expose = config.exposeApp ?: 'true'
    def replicas = config.replicas ?: '2'
    def requestCPU = config.resourceRequestCPU ?: '0'
    def requestMemory = config.resourceRequestMemory ?: '0'
    def limitCPU = config.resourceLimitCPU ?: '0'
    def limitMemory = config.resourceLimitMemory ?: '0'
    def ingressClass = config.ingressClass ?: 'unknown'
    def readinessProbePath = config.readinessProbePath ?: "/"
    def livenessProbePath = config.livenessProbePath ?: "/"
    def configMaptoMount = config.configMapToMount

    def yaml
    
    
    def list = """
---
apiVersion: v1
kind: List
items:
"""

def service = """
- apiVersion: v1
  kind: Service
  metadata:
    annotations:
      fabric8.io/ingress.path: /api/${config.projectName}/
      fabric8.io/ingress.annotations: |-
        ingress.kubernetes.io/rewrite-target: /
        ingress.kubernetes.io/force-ssl-redirect: true
        kubernetes.io/ingress.class: ${ingressClass}
    labels:
      provider: fabric8
      project: ${config.projectName}
      expose: '${expose}'
      version: ${config.version}
    name: ${config.projectName}
  spec:
    ports:
    - port: 80
      protocol: TCP
      targetPort: ${config.port}
    selector:
      project: ${config.projectName}
      provider: fabric8
"""

def deployment = """
- apiVersion: extensions/v1beta1
  kind: Deployment
  metadata:
    labels:
      provider: fabric8
      project: ${config.projectName}
      version: ${config.version}
    annotations:
      configmap.reloader.stakater.com/reload: ${config.projectName}
    name: ${config.projectName}
  spec:
    replicas: ${replicas}
    minReadySeconds: 5
    strategy:
      type: RollingUpdate
    selector:
      matchLabels:
        provider: fabric8
        project: ${config.projectName}
    template:
      metadata:
        labels:
          provider: fabric8
          project: ${config.projectName}
          version: ${config.version}
      spec:
        imagePullSecrets:
        - name: ${config.dockerRegistrySecret}
        terminationGracePeriodSeconds: 2
        containers:
        - env:
          - name: KUBERNETES_NAMESPACE
            valueFrom:
              fieldRef:
                fieldPath: metadata.namespace
          image: ${config.imageName}
          imagePullPolicy: IfNotPresent
          name: ${config.projectName}
          ports:
          - containerPort: ${config.port}
            name: http
          resources:
            limits:
              cpu: ${limitCPU}
              memory: ${limitMemory}
            requests:
              cpu: ${requestCPU}
              memory: ${requestMemory}
          readinessProbe:
            httpGet:
              path: "${readinessProbePath}"
              port: ${config.port}
            initialDelaySeconds: 1
            timeoutSeconds: 5
            failureThreshold: 5
          livenessProbe:
            httpGet:
              path: "${livenessProbePath}"
              port: ${config.port}
            initialDelaySeconds: 180
            timeoutSeconds: 5
            failureThreshold: 5
"""
    if(configMaptoMount) {
        def configMount = """
          volumeMounts:
          - name: config-volume
            mountPath: /etc/config
        volumes:
          - name: config-volume
            configMap:
              name: ${configMaptoMount}
"""
        deployment+=configMount
    }

    yaml = list + service + deployment
    echo 'using resources:\n' + yaml
    return yaml
}
