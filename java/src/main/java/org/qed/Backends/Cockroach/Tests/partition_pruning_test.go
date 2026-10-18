package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPartitionPruning(t *testing.T) {
	rule := loadRule(t, "PartitionPruning")
	e := newEnv(t)
	build, buildCols := e.scan("u", 1)
	probe, probeCols := e.scan("t", 1)
	condition := e.filters(e.f.ConstructEq(e.v(buildCols[0]), e.v(probeCols[0])))
	before := e.f.ConstructInnerJoin(build, probe, condition, memo.EmptyJoinPrivate)

	pruned := e.f.ConstructSemiJoin(build, probe, condition, memo.EmptyJoinPrivate)
	want := e.f.ConstructInnerJoin(pruned, probe, condition, memo.EmptyJoinPrivate)
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(build, probe, condition, memo.EmptyJoinPrivate)
	e.expectNoMatch(rule, nearMiss)
}
