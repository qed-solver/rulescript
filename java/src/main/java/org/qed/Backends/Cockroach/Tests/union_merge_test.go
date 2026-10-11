package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionMerge(t *testing.T) {
	rule := loadRule(t, "UnionMerge")
	e := newEnv(t)
	a, aCols := e.scan("t", 1)
	b, bCols := e.scan("u", 1)
	c, cCols := e.scan("t", 1)
	d, dCols := e.scan("u", 1)

	union := func(l, r memo.RelExpr, lc, rc opt.ColumnID) (memo.RelExpr, opt.ColumnID) {
		out := e.f.Metadata().AddColumn("union", types.Int)
		priv := &memo.SetPrivate{
			LeftCols:  opt.ColList{lc},
			RightCols: opt.ColList{rc},
			OutCols:   opt.ColList{out},
		}
		return e.f.ConstructUnionAll(l, r, priv), out
	}

	bc, bcCol := union(b, c, bCols[0], cCols[0])
	mid, midCol := union(a, bc, aCols[0], bcCol)
	before, _ := union(mid, d, midCol, dCols[0])

	ab, abCol := union(a, b, aCols[0], bCols[0])
	abc, abcCol := union(ab, c, abCol, cCols[0])
	want, _ := union(abc, d, abcCol, dCols[0])
	e.expectRewrite(rule, before, want)

	distinctOut := e.f.Metadata().AddColumn("union", types.Int)
	distinctOuter := e.f.ConstructUnion(mid, d, &memo.SetPrivate{
		LeftCols:  opt.ColList{midCol},
		RightCols: opt.ColList{dCols[0]},
		OutCols:   opt.ColList{distinctOut},
	})
	e.expectNoMatch(rule, distinctOuter)

	distinctInnerOut := e.f.Metadata().AddColumn("union", types.Int)
	distinctInner := e.f.ConstructUnion(b, c, &memo.SetPrivate{
		LeftCols:  opt.ColList{bCols[0]},
		RightCols: opt.ColList{cCols[0]},
		OutCols:   opt.ColList{distinctInnerOut},
	})
	distinctInnerMid, distinctInnerMidCol := union(a, distinctInner, aCols[0], distinctInnerOut)
	distinctInnerPlan, _ := union(distinctInnerMid, d, distinctInnerMidCol, dCols[0])
	e.expectNoMatch(rule, distinctInnerPlan)

	e.expectNoMatch(rule, want)
	e.expectNoMatch(rule, bc)
	e.expectNoMatch(rule, mid)
}
