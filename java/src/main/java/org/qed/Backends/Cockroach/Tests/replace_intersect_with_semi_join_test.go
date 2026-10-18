package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestReplaceIntersectWithSemiJoin(t *testing.T) {
	rule := loadRule(t, "ReplaceIntersectWithSemiJoin")
	e := newEnv(t)
	left, leftCols := e.scan("t", 2)
	right, rightCols := e.scan("u", 2)
	outCols := []opt.ColumnID{
		e.f.Metadata().AddColumn("intersect_out_0", e.f.Metadata().ColumnMeta(leftCols[0]).Type),
		e.f.Metadata().AddColumn("intersect_out_1", e.f.Metadata().ColumnMeta(leftCols[1]).Type),
	}
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{leftCols[0], leftCols[1]},
		RightCols: opt.ColList{rightCols[0], rightCols[1]},
		OutCols:   opt.ColList{outCols[0], outCols[1]},
	}
	before := e.f.ConstructIntersect(left, right, private)
	semi := e.f.ConstructSemiJoin(left, right, e.filters(
		e.f.ConstructIs(e.v(leftCols[0]), e.v(rightCols[0])),
		e.f.ConstructIs(e.v(leftCols[1]), e.v(rightCols[1])),
	), &memo.JoinPrivate{})
	grouped := e.f.ConstructGroupBy(semi, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(leftCols[0], leftCols[1]),
	})
	want := e.f.ConstructProject(grouped, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(leftCols[0]), outCols[0]),
		e.f.ConstructProjectionsItem(e.v(leftCols[1]), outCols[1]),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e2 := newEnv(t)
	left2, leftCols2 := e2.scan("t", 2)
	right2, rightCols2 := e2.scan("u", 2)
	outCols2 := []opt.ColumnID{
		e2.f.Metadata().AddColumn("intersect_all_out_0", e2.f.Metadata().ColumnMeta(leftCols2[0]).Type),
		e2.f.Metadata().AddColumn("intersect_all_out_1", e2.f.Metadata().ColumnMeta(leftCols2[1]).Type),
	}
	e2.expectNoMatch(rule, e2.f.ConstructIntersectAll(left2, right2, &memo.SetPrivate{
		LeftCols:  opt.ColList{leftCols2[0], leftCols2[1]},
		RightCols: opt.ColList{rightCols2[0], rightCols2[1]},
		OutCols:   opt.ColList{outCols2[0], outCols2[1]},
	}))
}
