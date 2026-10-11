package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionPullUpConstants(t *testing.T) {
	rule := loadRule(t, "UnionPullUpConstants")
	e := newEnv(t)
	a, ac := e.scan("t", 2)
	b, bc := e.scan("u", 2)
	lTrue := e.f.Metadata().AddColumn("true", types.Bool)
	rTrue := e.f.Metadata().AddColumn("true", types.Bool)
	out0 := e.f.Metadata().AddColumn("true", types.Bool)
	out1 := e.f.Metadata().AddColumn("c", types.Int)

	trueItem := func(col opt.ColumnID) memo.ProjectionsExpr {
		return memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), col)}
	}
	lProj := e.f.ConstructProject(a, trueItem(lTrue), opt.MakeColSet(ac[1]))
	rProj := e.f.ConstructProject(b, trueItem(rTrue), opt.MakeColSet(bc[1]))
	priv := &memo.SetPrivate{
		LeftCols:  opt.ColList{lTrue, ac[1]},
		RightCols: opt.ColList{rTrue, bc[1]},
		OutCols:   opt.ColList{out0, out1},
	}
	before := e.f.ConstructUnionAll(lProj, rProj, priv)

	innerL := e.f.ConstructProject(a, nil, opt.MakeColSet(ac[1]))
	innerR := e.f.ConstructProject(b, nil, opt.MakeColSet(bc[1]))
	inner := e.f.ConstructUnionAll(innerL, innerR, &memo.SetPrivate{
		LeftCols:  opt.ColList{ac[1]},
		RightCols: opt.ColList{bc[1]},
		OutCols:   opt.ColList{out1},
	})
	want := e.f.ConstructProject(inner, trueItem(out0), opt.MakeColSet(out1))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructUnion(lProj, rProj, priv))

	falseCol := e.f.Metadata().AddColumn("false", types.Bool)
	lFalse := e.f.ConstructProject(a, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), falseCol),
	}, opt.MakeColSet(ac[1]))
	e.expectNoMatch(rule, e.f.ConstructUnionAll(lFalse, rProj, &memo.SetPrivate{
		LeftCols:  opt.ColList{falseCol, ac[1]},
		RightCols: opt.ColList{rTrue, bc[1]},
		OutCols:   opt.ColList{out0, out1},
	}))

	rOther := e.f.ConstructProject(b, trueItem(rTrue), opt.MakeColSet(bc[0]))
	e.expectNoMatch(rule, e.f.ConstructUnionAll(lProj, rOther, &memo.SetPrivate{
		LeftCols:  opt.ColList{lTrue, ac[1]},
		RightCols: opt.ColList{rTrue, bc[0]},
		OutCols:   opt.ColList{out0, out1},
	}))
}
