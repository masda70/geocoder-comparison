# geocoder-comparison

Version: 0.0.1

Tool for comparison Geocoder results. Currently supports [Photon](https://github.com/komoot/photon).

Uses ElasticSearch's Java Transport Client v1.7.4.

Built using SBT.

```
geocoder-comparison
Usage: geocoder-comparison [options]

  -r <file> | --result1 <file>
        result file, e.g. result.json
  -t <file> | --result2 <file>
        second result file, e.g. result2.json
  -s <uri> | --service <uri>
        geocoder service url, e.g. http://localhost:2322
  -c <int> | --concurrency <int>
        concurrency
```