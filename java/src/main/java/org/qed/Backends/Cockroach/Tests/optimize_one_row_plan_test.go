package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestOptimizeOneRowPlan(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "OptimizeOneRowPlan")
	input, cols := e.scan("t", 1)
	grouping := memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}
	before := e.f.ConstructGroupBy(input, nil, &grouping)
	want := e.f.ConstructProject(input, nil, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, want)

	input, cols = e.scan("t", 2)
	grouping = memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[1])}
	nearMiss := e.f.ConstructGroupBy(input, nil, &grouping)
	e.expectNoMatch(rule, nearMiss)
}
