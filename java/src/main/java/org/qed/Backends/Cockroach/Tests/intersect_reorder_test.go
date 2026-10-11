package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestIntersectReorder(t *testing.T) {
	rule := loadRule(t, "IntersectReorder")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	out1 := e.f.Metadata().AddColumn("out1", types.Int)
	out2 := e.f.Metadata().AddColumn("out2", types.Int)
	out3 := e.f.Metadata().AddColumn("out3", types.Int)
	out4 := e.f.Metadata().AddColumn("out4", types.Int)

	ab := e.f.ConstructIntersect(a, b, &memo.SetPrivate{
		LeftCols: opt.ColList{ac[0]}, RightCols: opt.ColList{bc[0]}, OutCols: opt.ColList{out1},
	})
	outer := &memo.SetPrivate{
		LeftCols: opt.ColList{out1}, RightCols: opt.ColList{cc[0]}, OutCols: opt.ColList{out2},
	}
	before := e.f.ConstructIntersect(ab, c, outer)
	ca := e.f.ConstructIntersect(c, a, &memo.SetPrivate{
		LeftCols: opt.ColList{cc[0]}, RightCols: opt.ColList{ac[0]}, OutCols: opt.ColList{out3},
	})
	want := e.f.ConstructIntersect(ca, b, &memo.SetPrivate{
		LeftCols: opt.ColList{out3}, RightCols: opt.ColList{bc[0]}, OutCols: opt.ColList{out4},
	})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, ab)
	e.expectNoMatch(rule, e.f.ConstructIntersectAll(ab, c, outer))
	e.expectNoMatch(rule, e.f.ConstructUnion(ab, c, outer))

	bc3 := e.f.ConstructIntersect(b, c, &memo.SetPrivate{
		LeftCols: opt.ColList{bc[0]}, RightCols: opt.ColList{cc[0]}, OutCols: opt.ColList{out3},
	})
	rightNested := e.f.ConstructIntersect(a, bc3, &memo.SetPrivate{
		LeftCols: opt.ColList{ac[0]}, RightCols: opt.ColList{out3}, OutCols: opt.ColList{out4},
	})
	e.expectNoMatch(rule, rightNested)
}
