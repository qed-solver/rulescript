package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSetOpToFilter(t *testing.T) {
	rule := loadRule(t, "SetOpToFilter")

	t.Run("union distinct filters", func(t *testing.T) {
		e := newEnv(t)
		leftSource, leftCols := e.scan("u", 1)
		rightSource, rightCols := e.scan("u", 1)
		p1 := e.f.ConstructEq(e.v(leftCols[0]), e.num(1))
		p2 := e.f.ConstructEq(e.v(rightCols[0]), e.num(2))
		left := e.f.ConstructSelect(leftSource, e.filters(p1))
		right := e.f.ConstructSelect(rightSource, e.filters(p2))
		outCols := opt.ColList{
			e.f.Metadata().AddColumn("union_a", e.f.Metadata().ColumnMeta(leftCols[0]).Type),
		}
		before := e.f.ConstructUnion(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{leftCols[0]},
			RightCols: opt.ColList{rightCols[0]},
			OutCols: outCols,
		})

		or := e.f.ConstructOr(p1, e.f.ConstructEq(e.v(leftCols[0]), e.num(2)))
		filtered := e.f.ConstructSelect(leftSource, e.filters(or))
		grouped := e.f.ConstructGroupBy(filtered, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(leftCols[0])})
		want := e.f.ConstructProject(grouped, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(leftCols[0]), outCols[0]),
		}, opt.ColSet{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("union all near miss", func(t *testing.T) {
		e := newEnv(t)
		leftSource, leftCols := e.scan("u", 1)
		rightSource, rightCols := e.scan("u", 1)
		p1 := e.f.ConstructEq(e.v(leftCols[0]), e.num(1))
		p2 := e.f.ConstructEq(e.v(rightCols[0]), e.num(2))
		left := e.f.ConstructSelect(leftSource, e.filters(p1))
		right := e.f.ConstructSelect(rightSource, e.filters(p2))
		outCols := opt.ColList{
			e.f.Metadata().AddColumn("union_a", e.f.Metadata().ColumnMeta(leftCols[0]).Type),
		}
		plan := e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{leftCols[0]},
			RightCols: opt.ColList{rightCols[0]},
			OutCols: outCols,
		})
		e.expectNoMatch(rule, plan)
	})
}
