# CardDemo modernization target

Target repository for migrating the AWS CardDemo mainframe application
([`ipsorakis/aws-mainframe-modernization-carddemo`](https://github.com/ipsorakis/aws-mainframe-modernization-carddemo):
COBOL, CICS, BMS, VSAM, JCL) to a Python monolith.

Target stack:

- Python monolith (Django or FastAPI) on PostgreSQL (Cloud SQL), deployed as a container on GCP Cloud Run.
- Batch jobs (today's JCL + Control-M/CA-7 schedules) as Cloud Run Jobs triggered by Cloud Scheduler.

The work is tracked in Jira project **BNP** (label `carddemo-python-gcp`).

## Source inventory

[`docs/inventory/README.md`](docs/inventory/README.md) is the master index of the source repo: every program,
copybook, BMS map, JCL member, scheduler definition, CSD and data file, with their dependencies
(`inventory.csv`, `dependencies.csv`) and the script that regenerates and coverage-checks them.
