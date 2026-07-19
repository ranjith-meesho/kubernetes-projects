# 100 Essential kubectl Commands

## Cluster & Context (1-10)

1. `kubectl cluster-info` — Show master/API server and DNS service endpoints.
2. `kubectl version` — Show client and server Kubernetes versions.
3. `kubectl config get-contexts` — List all available contexts.
4. `kubectl config current-context` — Show the currently active context.
5. `kubectl config use-context <name>` — Switch to a different context/cluster.
6. `kubectl config view` — Display merged kubeconfig settings.
7. `kubectl config set-context --current --namespace=<ns>` — Change default namespace for current context.
8. `kubectl api-resources` — List all resource types supported by the cluster.
9. `kubectl api-versions` — List supported API versions.
10. `kubectl config delete-context <name>` — Remove a context from kubeconfig.

## Viewing & Finding Resources (11-25)

11. `kubectl get pods` — List Pods in the current namespace.
12. `kubectl get pods -A` — List Pods across all namespaces.
13. `kubectl get pods -o wide` — List Pods with extra columns (node, IP).
14. `kubectl get all` — List common resources (pods, svc, deploy, rs) in namespace.
15. `kubectl get nodes` — List cluster nodes and their status.
16. `kubectl describe pod <name>` — Show detailed Pod spec, status, and events.
17. `kubectl get pods --show-labels` — List Pods with all their labels.
18. `kubectl get pods -l app=<name>` — Filter Pods by label selector.
19. `kubectl get pods --field-selector=status.phase=Running` — Filter by field.
20. `kubectl get events --sort-by=.lastTimestamp` — List events, oldest to newest.
21. `kubectl get svc` — List Services in current namespace.
22. `kubectl get deploy` — List Deployments.
23. `kubectl get ns` — List all namespaces.
24. `kubectl get pv,pvc` — List PersistentVolumes and PersistentVolumeClaims.
25. `kubectl get pods --watch` — Stream live updates as Pod status changes.

## Creating & Applying (26-38)

26. `kubectl apply -f file.yaml` — Create or update resources declaratively from a file.
27. `kubectl apply -f dir/` — Apply all manifests in a directory.
28. `kubectl create -f file.yaml` — Imperatively create resources from a file.
29. `kubectl create deployment nginx --image=nginx` — Quickly create a Deployment.
30. `kubectl create namespace <name>` — Create a new namespace.
31. `kubectl run mypod --image=nginx` — Create a standalone Pod imperatively.
32. `kubectl expose deployment <name> --port=80` — Create a Service for a Deployment.
33. `kubectl create configmap <name> --from-literal=key=val` — Create a ConfigMap from literals.
34. `kubectl create secret generic <name> --from-literal=key=val` — Create a generic Secret.
35. `kubectl apply -f file.yaml --dry-run=client -o yaml` — Preview manifest without applying.
36. `kubectl create job myjob --image=busybox` — Create a one-off Job.
37. `kubectl create cronjob mycron --image=busybox --schedule="*/5 * * * *"` — Create a CronJob.
38. `kubectl apply -k dir/` — Apply resources using a Kustomize overlay.

## Updating & Patching (39-48)

39. `kubectl edit deployment <name>` — Open a resource in your editor to edit live.
40. `kubectl patch deployment <name> -p '{"spec":{"replicas":3}}'` — Patch a resource with a JSON/merge patch.
41. `kubectl set image deployment/<name> <container>=<image>` — Update a container image.
42. `kubectl label pod <name> env=prod` — Add/update a label on a resource.
43. `kubectl annotate pod <name> note="hello"` — Add/update an annotation.
44. `kubectl scale deployment <name> --replicas=5` — Change the replica count.
45. `kubectl autoscale deployment <name> --min=2 --max=10 --cpu-percent=80` — Create an HPA imperatively.
46. `kubectl set env deployment/<name> KEY=value` — Set/update environment variables.
47. `kubectl replace -f file.yaml` — Replace a resource entirely from file.
48. `kubectl taint nodes <node> key=value:NoSchedule` — Add a taint to a node.

## Deleting (49-56)

49. `kubectl delete pod <name>` — Delete a specific Pod.
50. `kubectl delete -f file.yaml` — Delete resources defined in a manifest file.
51. `kubectl delete deployment <name>` — Delete a Deployment (and its ReplicaSets/Pods).
52. `kubectl delete pods --all` — Delete all Pods in the current namespace.
53. `kubectl delete pod <name> --grace-period=0 --force` — Force-delete a stuck Pod.
54. `kubectl delete namespace <name>` — Delete a namespace and everything inside it.
55. `kubectl delete pods -l app=<name>` — Delete Pods matching a label selector.
56. `kubectl delete svc,deploy -l app=<name>` — Delete multiple resource types by label.

## Debugging & Logs (57-68)

57. `kubectl logs <pod>` — View container logs.
58. `kubectl logs <pod> -f` — Stream (follow) logs in real time.
59. `kubectl logs <pod> --previous` — View logs from a previous (crashed) container instance.
60. `kubectl logs <pod> -c <container>` — View logs for a specific container in a multi-container Pod.
61. `kubectl logs -l app=<name> --all-containers` — Stream logs from all Pods matching a label.
62. `kubectl describe node <name>` — Show node capacity, conditions, and scheduled Pods.
63. `kubectl get events -n <namespace>` — List events in a namespace to spot errors.
64. `kubectl top pod` — Show live CPU/memory usage per Pod (requires metrics-server).
65. `kubectl top node` — Show live CPU/memory usage per node.
66. `kubectl debug pod/<name> -it --image=busybox` — Attach an ephemeral debug container to a Pod.
67. `kubectl get pod <name> -o yaml` — Dump full resource definition including status.
68. `kubectl rollout status deployment/<name>` — Watch a rollout until it completes or fails.

## Exec & Port-forward (69-75)

69. `kubectl exec -it <pod> -- /bin/bash` — Open an interactive shell inside a container.
70. `kubectl exec <pod> -- env` — Run a one-off command inside a container.
71. `kubectl exec -it <pod> -c <container> -- sh` — Exec into a specific container in a multi-container Pod.
72. `kubectl port-forward pod/<name> 8080:80` — Forward local port to a Pod's port.
73. `kubectl port-forward svc/<name> 8080:80` — Forward local port to a Service's port.
74. `kubectl port-forward deployment/<name> 8080:80` — Forward local port to a Deployment's Pod.
75. `kubectl cp <pod>:/path/file ./file` — Copy a file out of a container to local disk.

## Scaling & Rollouts (76-83)

76. `kubectl rollout history deployment/<name>` — List revision history of a Deployment.
77. `kubectl rollout undo deployment/<name>` — Roll back to the previous revision.
78. `kubectl rollout undo deployment/<name> --to-revision=2` — Roll back to a specific revision.
79. `kubectl rollout pause deployment/<name>` — Pause an in-progress rollout.
80. `kubectl rollout resume deployment/<name>` — Resume a paused rollout.
81. `kubectl rollout restart deployment/<name>` — Trigger a fresh rolling restart of all Pods.
82. `kubectl scale --replicas=0 deployment/<name>` — Scale a Deployment down to zero (stop it).
83. `kubectl get hpa` — List HorizontalPodAutoscalers and their current metrics/targets.

## Namespaces (84-88)

84. `kubectl get pods -n kube-system` — List Pods in a specific namespace.
85. `kubectl get resourcequota -n <namespace>` — Show resource quotas set on a namespace.
86. `kubectl describe limitrange -n <namespace>` — Show default/limit constraints on a namespace.
87. `kubectl get all -n <namespace>` — List all common resources within a namespace.
88. `kubectl config set-context --current --namespace=<ns>` — Persistently switch default namespace.

## RBAC & Auth (89-93)

89. `kubectl auth can-i create pods` — Check if current user/context can perform an action.
90. `kubectl auth can-i list secrets --as=system:serviceaccount:default:myapp` — Check permissions for a specific ServiceAccount.
91. `kubectl get rolebindings,clusterrolebindings -A` — List all role bindings across the cluster.
92. `kubectl create rolebinding --clusterrole=view --serviceaccount=default:myapp --dry-run=client -o yaml` — Generate a RoleBinding manifest.
93. `kubectl get sa` — List ServiceAccounts in the current namespace.

## ConfigMaps & Secrets (94-97)

94. `kubectl get configmap <name> -o yaml` — View a ConfigMap's full contents.
95. `kubectl get secret <name> -o jsonpath='{.data.password}' | base64 -d` — Decode a Secret value.
96. `kubectl create secret docker-registry regcred --docker-server=... --docker-username=...` — Create an image-pull Secret.
97. `kubectl describe configmap <name>` — Show ConfigMap keys (values hidden for secrets).

## Node & Resource Inspection (98-99)

98. `kubectl describe node <name> | grep -A5 "Allocated resources"` — Check requests/limits vs node capacity.
99. `kubectl get pods -o wide --field-selector spec.nodeName=<node>` — List Pods scheduled on a specific node.

## Advanced / JSONPath / Output Formatting (100)

100. `kubectl get pods -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.phase}{"\n"}{end}'` — Extract custom fields across all Pods using JSONPath.
