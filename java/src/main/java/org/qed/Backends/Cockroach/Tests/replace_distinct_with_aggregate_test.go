package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestReplaceDistinctWithAggregate(t *testing.T) {
	rule := loadRule(t, "ReplaceDistinctWithAggregate")
	e := newEnv(t)
	left, leftCols := e.scan("nn", 2)
	right, rightCols := e.scan("nn", 2)
	outCols := []opt.ColumnID{
		e.f.Metadata().AddColumn("out_a", e.f.Metadata().ColumnMeta(leftCols[0]).Type),
		e.f.Metadata().AddColumn("out_b", e.f.Metadata().ColumnMeta(leftCols[1]).Type),
	}
	set := &memo.SetPrivate{
		LeftCols:  opt.ColList{leftCols[0], leftCols[1]},
		RightCols: opt.ColList{rightCols[0], rightCols[1]},
		OutCols:   opt.ColList{outCols[0], outCols[1]},
	}
	before := e.f.ConstructIntersect(left, right, set)
	want := e.f.ConstructProject(e.f.ConstructGroupBy(left, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(leftCols...),
	}), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(leftCols[0]), outCols[0]),
		e.f.ConstructProjectionsItem(e.v(leftCols[1]), outCols[1]),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	left, leftCols = e.scan("nn", 2)
	right, rightCols = e.scan("nn", 2)
	outCols = []opt.ColumnID{
		e.f.Metadata().AddColumn("all_a", e.f.Metadata().ColumnMeta(leftCols[0]).Type),
		e.f.Metadata().AddColumn("all_b", e.f.Metadata().ColumnMeta(leftCols[1]).Type),
	}
	e.expectNoMatch(rule, e.f.ConstructIntersectAll(left, right, &memo.SetPrivate{
		LeftCols:  opt.ColList{leftCols[0], leftCols[1]},
		RightCols: opt.ColList{rightCols[0], rightCols[1]},
		OutCols:   opt.ColList{outCols[0], outCols[1]},
	}))
}
