package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestReduceNotNullGroupingCols(t *testing.T) {
	rule := loadRule(t, "ReduceNotNullGroupingCols")

	e := newEnv(t)
	scan, scanCols := e.scan("nn", 1)
	second := e.f.Metadata().AddColumn("second", types.Int)
	input := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(scanCols[0]), second),
	}, opt.MakeColSet(scanCols[0]))
	grouped := e.f.ConstructGroupBy(input, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(scanCols[0], second)})
	reduced := e.f.ConstructGroupBy(input, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(scanCols[0])})
	want := e.f.ConstructProject(reduced, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(scanCols[0]), second),
	}, opt.MakeColSet(scanCols[0]))
	e.expectRewriteUpToFreshColumns(rule, grouped, want)

	e2 := newEnv(t)
	scan2, scanCols2 := e2.scan("nn", 1)
	second2 := e2.f.Metadata().AddColumn("second", types.Int)
	input2 := e2.f.ConstructProject(scan2, memo.ProjectionsExpr{
		e2.f.ConstructProjectionsItem(e2.v(scanCols2[0]), second2),
	}, opt.MakeColSet(scanCols2[0]))
	nearMiss := e2.f.ConstructGroupBy(input2, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(scanCols2[0])})
	e2.expectNoMatch(rule, nearMiss)
}
