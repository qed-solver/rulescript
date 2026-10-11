package rulescript_test

import (
	"context"
	"fmt"
	"os"
	"regexp"
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/settings/cluster"
	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/norm"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/optbuilder"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/rulescript"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/testutils/testcat"
	"github.com/cockroachdb/cockroach/pkg/sql/parser"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/eval"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
	"github.com/cockroachdb/cockroach/pkg/testutils/datapathutils"
	"github.com/cockroachdb/cockroach/pkg/util/buildutil"
)

type env struct {
	t       *testing.T
	evalCtx eval.Context
	cat     *testcat.Catalog
	f       norm.Factory
}

func newEnv(t *testing.T) *env {
	e := &env{t: t, cat: testcat.New()}
	e.evalCtx = eval.MakeTestingEvalContext(cluster.MakeTestingClusterSettings())
	for _, ddl := range []string{
		"CREATE TABLE t (a INT PRIMARY KEY, b INT, c INT)",
		"CREATE TABLE u (x INT PRIMARY KEY, y INT)",
		"CREATE TABLE nn (a INT PRIMARY KEY, b INT NOT NULL)",
		"CREATE TABLE nn3 (a INT PRIMARY KEY, b INT NOT NULL, c INT NOT NULL)",
	} {
		if _, err := e.cat.ExecuteDDL(ddl); err != nil {
			t.Fatal(err)
		}
	}
	e.f.Init(context.Background(), &e.evalCtx, e.cat)
	e.f.DisableOptimizations()
	return e
}

var inlineRules = map[string]string{}

func loadRule(t *testing.T, name string) *rulescript.Rule {
	if text, ok := inlineRules[name]; ok {
		rule, err := rulescript.Parse([]byte(text))
		if err != nil {
			t.Fatal(err)
		}
		return rule
	}
	data, err := os.ReadFile(datapathutils.TestDataPath(t, "rules", name+".json"))
	if err != nil {
		t.Fatal(err)
	}
	rule, err := rulescript.Parse(data)
	if err != nil {
		t.Fatal(err)
	}
	return rule
}

func (e *env) scan(table string, ncols int) (memo.RelExpr, []opt.ColumnID) {
	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, tree.Name(table))
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	cols := make([]opt.ColumnID, ncols)
	for i := range cols {
		cols[i] = tabID.ColumnID(i)
	}
	return e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(cols...)}), cols
}

func (e *env) v(c opt.ColumnID) opt.ScalarExpr { return e.f.ConstructVariable(c) }

func (e *env) empty(cols ...opt.ColumnID) memo.RelExpr {
	return e.f.ConstructValues(nil, &memo.ValuesPrivate{
		Cols: append(opt.ColList{}, cols...),
		ID:   e.f.Metadata().NextUniqueID(),
	})
}

func (e *env) num(n int) opt.ScalarExpr {
	return e.f.ConstructConst(tree.NewDInt(tree.DInt(n)), types.Int)
}

func (e *env) filters(conds ...opt.ScalarExpr) memo.FiltersExpr {
	out := make(memo.FiltersExpr, len(conds))
	for i, c := range conds {
		out[i] = e.f.ConstructFiltersItem(c)
	}
	return out
}

func (e *env) format(r opt.Expr) string {
	var sb strings.Builder
	canon(&sb, r)
	return sb.String()
}

func canon(sb *strings.Builder, x opt.Expr) {
	switch t := x.(type) {
	case *memo.ScanExpr:
		fmt.Fprintf(sb, "(scan %v)", t.Cols)
		return
	case *memo.ValuesExpr:
		fmt.Fprintf(sb, "(values cols=%v", t.Private().(*memo.ValuesPrivate).Cols)
		for _, row := range t.Rows {
			sb.WriteString(" ")
			canon(sb, row)
		}
		sb.WriteString(")")
		return
	case *memo.VariableExpr:
		fmt.Fprintf(sb, "(var %d)", t.Col)
		return
	case *memo.ConstExpr:
		fmt.Fprintf(sb, "(const %s)", t.Value.String())
		return
	case *memo.ProjectionsItem:
		fmt.Fprintf(sb, "(item %d ", t.Col)
		canon(sb, t.Element)
		sb.WriteString(")")
		return
	case *memo.ProjectExpr:
		fmt.Fprintf(sb, "(project pass=%v ", t.Passthrough)
		canon(sb, t.Input)
		sb.WriteString(" ")
		canon(sb, &t.Projections)
		sb.WriteString(")")
		return
	}
	sb.WriteString("(" + x.Op().String())
	for i, n := 0, x.ChildCount(); i < n; i++ {
		sb.WriteString(" ")
		canon(sb, x.Child(i))
	}
	sb.WriteString(")")
}

func (e *env) expectRewrite(rule *rulescript.Rule, before, want memo.RelExpr) {
	e.t.Helper()
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		e.t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	if g, w := e.format(got), e.format(want); g != w {
		e.t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), g, w)
	}
	if e.t.Name() == "TestMatchesNativeRules/"+rule.Name {
		e.compareNativeRoot(rule.Name, before, got)
	}
}

func (e *env) compareNativeRoot(name string, before, generated memo.RelExpr) {
	e.t.Helper()
	args := make([]interface{}, 0, before.ChildCount()+1)
	for i := 0; i < before.ChildCount(); i++ {
		args = append(args, before.Child(i))
	}
	if private := before.Private(); private != nil {
		args = append(args, private)
	}
	nativeMatches := 0
	e.f.NotifyOnMatchedRule(func(r opt.RuleName) bool {
		if r.String() == name {
			nativeMatches++
			return true
		}
		return false
	})
	native, ok := e.f.DynamicConstruct(before.Op(), args...).(memo.RelExpr)
	if !ok {
		e.t.Fatalf("%s native reconstruction was not relational", name)
	}
	if nativeMatches == 0 {
		if e.format(generated) == e.format(native) {
			e.t.Fatalf("%s unexpectedly matched the native plan without a native rule match", name)
		}
		e.t.Logf("%s: native rule did not match this RuleScript port shape", name)
		return
	}
	if count, ok := nativeAlphaColumns[name]; ok {
		generatedShape, generatedCount := alphaComputedColumns(e.format(generated))
		nativeShape, nativeCount := alphaComputedColumns(e.format(native))
		if generatedCount != count || nativeCount != count || generatedShape != nativeShape {
			e.t.Fatalf("%s differs from native beyond fresh computed column IDs:\n%s\nnative:\n%s",
				name, e.format(generated), e.format(native))
		}
		return
	}
	if reason, expected := nativeRootDifferences[name]; expected {
		if e.format(generated) == e.format(native) {
			e.t.Fatalf("%s unexpectedly matched native plan despite documented difference: %s", name, reason)
		}
		return
	}
	if e.format(generated) != e.format(native) {
		e.t.Fatalf("%s generated plan differs from native after %d matches:\n%s\nnative:\n%s",
			name, nativeMatches, e.format(generated), e.format(native))
	}
}

var computedItemID = regexp.MustCompile(`\(item ([0-9]+) `)

func alphaComputedColumns(plan string) (string, int) {
	matches := computedItemID.FindAllStringSubmatch(plan, -1)
	seen := make(map[string]string)
	for _, match := range matches {
		id := match[1]
		if _, ok := seen[id]; ok {
			continue
		}
		label := fmt.Sprintf("fresh%d", len(seen)+1)
		seen[id] = label
	}
	for id, label := range seen {
		plan = strings.ReplaceAll(plan, "(item "+id+" ", "(item "+label+" ")
		plan = strings.ReplaceAll(plan, "(var "+id+")", "(var "+label+")")
	}
	return plan, len(seen)
}

func (e *env) expectNoMatch(rule *rulescript.Rule, plan memo.RelExpr) {
	e.t.Helper()
	if got, ok := rule.Apply(&e.f, plan); ok {
		e.t.Fatalf("%s applied to a plan it does not match:\n%s\nproducing:\n%s",
			rule.Name, e.format(plan), e.format(got))
	}
}

func planFor(t *testing.T, sql string, native func(opt.RuleName) bool, ruleNames ...string) (string, []string) {
	e := newEnv(t)
	var rules []*rulescript.Rule
	for _, name := range ruleNames {
		rules = append(rules, loadRule(t, name))
	}
	installed := rulescript.Install(&e.f, rules...)
	e.f.NotifyOnMatchedRule(func(r opt.RuleName) bool {
		if r == opt.ExternalNormalizeRule {
			return true
		}
		return native != nil && native(r)
	})

	stmt, err := parser.ParseOne(sql)
	if err != nil {
		t.Fatal(err)
	}
	semaCtx := tree.MakeSemaContext(e.cat)
	semaCtx.SearchPath = &e.evalCtx.SessionData().SearchPath
	semaCtx.Placeholders.Init(stmt.NumPlaceholders, nil)
	semaCtx.Annotations = tree.MakeAnnotations(stmt.NumAnnotations)
	if err := optbuilder.New(
		context.Background(), &semaCtx, &e.evalCtx, e.cat, &e.f, stmt.AST,
	).Build(); err != nil {
		t.Fatal(err)
	}
	plan := memo.FormatExpr(
		context.Background(), e.f.Memo().RootExpr(), (memo.ExprFmtHideAll&^memo.ExprFmtHideColumns)|memo.ExprFmtHideQualifications,
		false, e.f.Memo(), e.cat,
	)
	return plan, installed.Applied
}

func TestInvariantChecksEnabled(t *testing.T) {
	if !buildutil.CrdbTestBuild {
		t.Skip("memo expression checks are only compiled into crdb_test builds")
	}
}
