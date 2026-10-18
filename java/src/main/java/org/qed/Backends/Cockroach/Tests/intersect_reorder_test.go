package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestIntersectReorder(t *testing.T) {
	rule := loadRule(t, "IntersectReorder")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	ab := e.f.Metadata().AddColumn("ab", e.f.Metadata().ColumnMeta(ac[0]).Type)
	abNode := e.f.ConstructIntersect(a, b, &memo.SetPrivate{
		LeftCols:  opt.ColList{ac[0]},
		RightCols: opt.ColList{bc[0]},
		OutCols:   opt.ColList{ab},
	})
	root := e.f.Metadata().AddColumn("abc", e.f.Metadata().ColumnMeta(ab).Type)
	before := e.f.ConstructIntersect(abNode, c, &memo.SetPrivate{
		LeftCols:  opt.ColList{ab},
		RightCols: opt.ColList{cc[0]},
		OutCols:   opt.ColList{root},
	})
	ca := e.f.Metadata().AddColumn("ca", e.f.Metadata().ColumnMeta(ac[0]).Type)
	caNode := e.f.ConstructIntersect(c, a, &memo.SetPrivate{
		LeftCols:  opt.ColList{cc[0]},
		RightCols: opt.ColList{ac[0]},
		OutCols:   opt.ColList{ca},
	})
	want := e.f.Metadata().AddColumn("cab", e.f.Metadata().ColumnMeta(ca).Type)
	expected := e.f.ConstructIntersect(caNode, b, &memo.SetPrivate{
		LeftCols:  opt.ColList{ca},
		RightCols: opt.ColList{bc[0]},
		OutCols:   opt.ColList{want},
	})
	e.expectRewrite(rule, before, expected)
	e.expectNoMatch(rule, e.f.ConstructIntersectAll(abNode, c, &memo.SetPrivate{
		LeftCols:  opt.ColList{ab},
		RightCols: opt.ColList{cc[0]},
		OutCols:   opt.ColList{root},
	}))
}
