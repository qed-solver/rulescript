package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestReplaceDeduplicateWithAggregate(t *testing.T) {
	rule := loadRule(t, "ReplaceDeduplicateWithAggregate")
	e := newEnv(t)
	left, leftCols := e.scan("t", 2)
	right, rightCols := e.scan("t", 2)
	outCols := opt.ColList{
		e.f.Metadata().AddColumn("out_a", types.Int),
		e.f.Metadata().AddColumn("out_b", types.Int),
	}
	set := &memo.SetPrivate{
		LeftCols:  opt.ColList{leftCols[0], leftCols[1]},
		RightCols: opt.ColList{rightCols[0], rightCols[1]},
		OutCols:   outCols,
	}
	before := e.f.ConstructIntersect(left, right, set)
	want := e.f.ConstructProject(e.f.ConstructGroupBy(left, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(leftCols...)}),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(leftCols[0]), outCols[0]),
			e.f.ConstructProjectionsItem(e.v(leftCols[1]), outCols[1]),
		}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	all := e.f.ConstructIntersectAll(left, right, set)
	e.expectNoMatch(rule, all)
}
